param(
    [string]$ApiUrl = 'http://127.0.0.1:8080/api/regulations/documents/pdf',
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '..\data\official-documents'),
    [string[]]$Jurisdictions = @(),
    [switch]$DownloadOnly
)

$ErrorActionPreference = 'Stop'
$catalogPath = Join-Path $PSScriptRoot '..\docs\municipal-document-catalog.json'
$ingestionScript = Join-Path $PSScriptRoot 'ingest-official-pdf.ps1'
$catalog = Get-Content -LiteralPath $catalogPath -Raw -Encoding utf8 | ConvertFrom-Json
$selectedDocumentCount = 0
$downloadedDocumentCount = 0
$downloadFailureCount = 0
$resolvedOutput = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $resolvedOutput -Force | Out-Null

if (-not $DownloadOnly -and [string]::IsNullOrWhiteSpace($env:NORMBUILD_INGESTION_API_KEY)) {
    throw 'Set NORMBUILD_INGESTION_API_KEY before importing documents.'
}

function Test-OfficialHost([string]$Url) {
    $uri = [Uri]$Url
    return $uri.Scheme -eq 'https' -and $uri.DnsSafeHost.EndsWith('.gov.co', [StringComparison]::OrdinalIgnoreCase)
}

foreach ($document in $catalog.documents) {
    if ($Jurisdictions.Count -gt 0 -and $document.jurisdiction -notin $Jurisdictions) {
        continue
    }
    $selectedDocumentCount++
    if (-not (Test-OfficialHost $document.sourceUrl)) {
        throw "The source URL is not an official HTTPS .gov.co address: $($document.sourceUrl)"
    }

    $safeName = ($document.jurisdiction + '-' + $document.regulationCode) -replace '[^A-Za-z0-9-]', '-'
    $pdfPath = Join-Path $resolvedOutput ($safeName + '.pdf')
    $partialPath = $pdfPath + '.download'
    $response = $null
    try {
        Write-Output "Downloading $($document.title) from its official source..."
        if (Test-Path -LiteralPath $partialPath) {
            Remove-Item -LiteralPath $partialPath -Force
        }
        $request = [Net.HttpWebRequest]::Create($document.sourceUrl)
        $request.Method = 'GET'
        $request.UserAgent = 'NormBuild-Official-Document-Importer/1.0'
        $request.Timeout = 180000
        $request.ReadWriteTimeout = 180000
        $request.MaximumAutomaticRedirections = 5
        $response = $request.GetResponse()
        $finalUri = $response.ResponseUri
        if (-not (Test-OfficialHost $finalUri.AbsoluteUri)) {
            throw "The official source redirected outside a .gov.co host: $($finalUri.Host)"
        }
        if ($response.ContentLength -gt 40MB) {
            throw 'The document exceeds the API 40 MB upload limit.'
        }
        $sourceStream = $response.GetResponseStream()
        $fileStream = [IO.File]::Create($partialPath)
        try {
            $sourceStream.CopyTo($fileStream)
        }
        finally {
            $fileStream.Dispose()
            $sourceStream.Dispose()
            $response.Dispose()
        }
        if ((Get-Item -LiteralPath $partialPath).Length -gt 40MB) {
            throw 'The document exceeds the API 40 MB upload limit.'
        }
        $stream = [IO.File]::OpenRead($partialPath)
        try {
            $signature = [byte[]]::new(5)
            $read = $stream.Read($signature, 0, $signature.Length)
        }
        finally {
            $stream.Dispose()
        }
        if ($read -ne 5 -or [Text.Encoding]::ASCII.GetString($signature) -ne '%PDF-') {
            throw "The official URL did not return a valid PDF: $($document.sourceUrl)"
        }
        Move-Item -LiteralPath $partialPath -Destination $pdfPath -Force
    }
    catch {
        $failureMessage = $_.Exception.Message
        $downloadFailureCount++
        if ($response) {
            $response.Dispose()
        }
        if (Test-Path -LiteralPath $partialPath) {
            Remove-Item -LiteralPath $partialPath -Force
        }
        $failureAuditPath = Join-Path $resolvedOutput 'download-failures.csv'
        $failureRecord = [PSCustomObject]@{
            AttemptedAtUtc = [DateTime]::UtcNow.ToString('o')
            Jurisdiction = $document.jurisdiction
            RegulationCode = $document.regulationCode
            SourceUrl = $document.sourceUrl
            Error = $failureMessage
        }
        if (Test-Path -LiteralPath $failureAuditPath) {
            $failureRecord | Export-Csv -LiteralPath $failureAuditPath -NoTypeInformation -Append -Encoding UTF8
        } else {
            $failureRecord | Export-Csv -LiteralPath $failureAuditPath -NoTypeInformation -Encoding UTF8
        }
        Write-Warning "Could not download $($document.jurisdiction) source; failure recorded and the next catalog entry will be tried. $failureMessage"
        continue
    }

    $checksum = (Get-FileHash -LiteralPath $pdfPath -Algorithm SHA256).Hash
    $downloadedDocumentCount++
    Write-Output "Downloaded and verified PDF. SHA-256: $checksum"
    if ($DownloadOnly) {
        $auditPath = Join-Path $resolvedOutput 'ingestion-audit.csv'
        $auditRecord = [PSCustomObject]@{
            ImportedAtUtc = [DateTime]::UtcNow.ToString('o')
            Jurisdiction = $document.jurisdiction
            RegulationCode = $document.regulationCode
            SourceUrl = $document.sourceUrl
            Sha256 = $checksum
            IndexedChunks = 0
            Status = 'DOWNLOADED_FOR_REVIEW'
        }
        if (Test-Path -LiteralPath $auditPath) {
            $auditRecord | Export-Csv -LiteralPath $auditPath -NoTypeInformation -Append -Encoding UTF8
        } else {
            $auditRecord | Export-Csv -LiteralPath $auditPath -NoTypeInformation -Encoding UTF8
        }
        continue
    }

    $confirmation = Read-Host "Confirm that you reviewed current validity and amendments for $($document.jurisdiction) by typing IMPORT"
    if ($confirmation -cne 'IMPORT') {
        Write-Output 'Skipped indexing until the legal-status review is complete.'
        continue
    }

    $ingestionResponse = & $ingestionScript -PdfPath $pdfPath -Title $document.title -Jurisdiction $document.jurisdiction `
        -RegulationCode $document.regulationCode -SourceUrl $document.sourceUrl -ApiUrl $ApiUrl
    $ingestionResult = ($ingestionResponse | Out-String | ConvertFrom-Json)
    $auditPath = Join-Path $resolvedOutput 'ingestion-audit.csv'
    $auditRecord = [PSCustomObject]@{
        ImportedAtUtc = [DateTime]::UtcNow.ToString('o')
        Jurisdiction = $document.jurisdiction
        RegulationCode = $document.regulationCode
        SourceUrl = $document.sourceUrl
        Sha256 = $checksum
        IndexedChunks = $ingestionResult.indexedChunks
        Status = $ingestionResult.status
    }
    if (Test-Path -LiteralPath $auditPath) {
        $auditRecord | Export-Csv -LiteralPath $auditPath -NoTypeInformation -Append -Encoding UTF8
    } else {
        $auditRecord | Export-Csv -LiteralPath $auditPath -NoTypeInformation -Encoding UTF8
    }
}

if ($selectedDocumentCount -eq 0) {
    $requestedJurisdictions = $Jurisdictions -join ', '
    Write-Output "No catalog documents matched the requested jurisdictions: $requestedJurisdictions."
    exit 2
}
Write-Output "Download summary: $downloadedDocumentCount downloaded, $downloadFailureCount failed, $selectedDocumentCount selected."
if ($downloadFailureCount -gt 0) {
    exit 1
}
