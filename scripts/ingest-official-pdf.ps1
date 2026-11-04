param(
    [Parameter(Mandatory = $true)]
    [string]$PdfPath,
    [Parameter(Mandatory = $true)]
    [string]$Title,
    [Parameter(Mandatory = $true)]
    [string]$Jurisdiction,
    [Parameter(Mandatory = $true)]
    [string]$RegulationCode,
    [Parameter(Mandatory = $true)]
    [string]$SourceUrl,
    [string]$ApiUrl = 'http://127.0.0.1:8080/api/regulations/documents/pdf'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http

if ([string]::IsNullOrWhiteSpace($env:NORMBUILD_INGESTION_API_KEY)) {
    throw 'Set NORMBUILD_INGESTION_API_KEY before importing a document.'
}

$sourceUri = [Uri]$SourceUrl
if ($sourceUri.Scheme -ne 'https' -or -not $sourceUri.DnsSafeHost.EndsWith('.gov.co', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'SourceUrl must be an HTTPS address on an official .gov.co domain.'
}

$resolvedPath = (Resolve-Path -LiteralPath $PdfPath).Path
$pdfBytes = [IO.File]::ReadAllBytes($resolvedPath)
if ($pdfBytes.Length -lt 5 -or [Text.Encoding]::ASCII.GetString($pdfBytes, 0, 5) -ne '%PDF-') {
    throw 'The selected file is not a valid PDF.'
}
if ($pdfBytes.Length -gt 40MB) {
    throw 'PDF files must be 40 MB or smaller.'
}

$client = [Net.Http.HttpClient]::new()
$client.Timeout = [TimeSpan]::FromMinutes(30)
$multipart = [Net.Http.MultipartFormDataContent]::new()
$request = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Post, $ApiUrl)

try {
    $metadata = @{
        title = $Title
        jurisdiction = $Jurisdiction
        regulationCode = $RegulationCode
        sourceUrl = $SourceUrl
    } | ConvertTo-Json -Compress
    $metadataContent = [Net.Http.StringContent]::new($metadata, [Text.Encoding]::UTF8, 'application/json')
    $fileContent = [Net.Http.ByteArrayContent]::new($pdfBytes)
    $fileContent.Headers.ContentType = [Net.Http.Headers.MediaTypeHeaderValue]::new('application/pdf')
    $multipart.Add($metadataContent, 'metadata')
    $multipart.Add($fileContent, 'file', [IO.Path]::GetFileName($resolvedPath))
    $request.Headers.Add('X-NormBuild-Ingestion-Key', $env:NORMBUILD_INGESTION_API_KEY)
    $request.Content = $multipart

    $response = $client.SendAsync($request).GetAwaiter().GetResult()
    $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    if (-not $response.IsSuccessStatusCode) {
        throw "Document ingestion failed with HTTP $([int]$response.StatusCode): $body"
    }
    Write-Output $body
}
finally {
    $request.Dispose()
    $client.Dispose()
}
