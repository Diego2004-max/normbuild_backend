param(
    [string]$CatalogPath = (Join-Path $PSScriptRoot '..\docs\municipal-document-catalog.json')
)

$ErrorActionPreference = 'Stop'
$failures = [System.Collections.Generic.List[string]]::new()
$resolvedCatalogPath = [IO.Path]::GetFullPath($CatalogPath)
if (-not (Test-Path -LiteralPath $resolvedCatalogPath -PathType Leaf)) {
    Write-Error "Municipal document catalog not found: $resolvedCatalogPath"
    exit 1
}

try {
    $catalog = Get-Content -LiteralPath $resolvedCatalogPath -Raw -Encoding utf8 | ConvertFrom-Json
} catch {
    Write-Error "Municipal document catalog is not valid UTF-8 JSON: $($_.Exception.Message)"
    exit 1
}

$requiredCities = @(
    ('Bogot' + [char]0x00E1 + ' D.C.')
    'Pasto'
    ('Medell' + [char]0x00ED + 'n')
    'Cali'
    'Bucaramanga'
    'Barranquilla'
    'Cartagena'
)
if (-not $catalog.documents -or $catalog.documents.Count -eq 0) {
    $failures.Add('The catalog contains no source documents.')
}

$seenMunicipalities = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$seenSourceUrls = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($document in $catalog.documents) {
    foreach ($field in @('title', 'jurisdiction', 'regulationCode', 'sourceUrl')) {
        $value = [string]$document.$field
        if ([string]::IsNullOrWhiteSpace($value)) {
            $failures.Add("A catalog document is missing $field.")
        } elseif ($value.Contains([char]0xFFFD) -or ($field -ne 'sourceUrl' -and $value.Contains('?'))) {
            $failures.Add("Catalog field $field contains a replacement character or an unresolved character: $value")
        }
    }
    if ($document.jurisdiction -and $document.jurisdiction -ne 'Colombia') {
        [void]$seenMunicipalities.Add([string]$document.jurisdiction)
    }

    foreach ($urlField in @('sourceUrl', 'registryUrl')) {
        $urlValue = [string]$document.$urlField
        if ([string]::IsNullOrWhiteSpace($urlValue)) {
            if ($urlField -eq 'sourceUrl') { continue }
        } else {
            $uri = $null
            if (-not [Uri]::TryCreate($urlValue, [UriKind]::Absolute, [ref]$uri) -or
                $uri.Scheme -ne 'https' -or
                -not $uri.DnsSafeHost.EndsWith('.gov.co', [StringComparison]::OrdinalIgnoreCase)) {
                $failures.Add("$urlField must use HTTPS on an official .gov.co host: $urlValue")
            }
        }
    }

    if ($document.sourceUrl -and -not $seenSourceUrls.Add([string]$document.sourceUrl)) {
        $failures.Add("Duplicate source URL: $($document.sourceUrl)")
    }
    foreach ($relatedSource in $document.relatedOfficialSources) {
        $relatedUri = $null
        $relatedUrl = [string]$relatedSource.url
        if ([string]::IsNullOrWhiteSpace($relatedSource.title) -or
            $relatedSource.title.Contains([char]0xFFFD) -or $relatedSource.title.Contains('?') -or
            -not [Uri]::TryCreate($relatedUrl, [UriKind]::Absolute, [ref]$relatedUri) -or
            $relatedUri.Scheme -ne 'https' -or
            -not $relatedUri.DnsSafeHost.EndsWith('.gov.co', [StringComparison]::OrdinalIgnoreCase)) {
            $failures.Add("Related source must have a title and official HTTPS .gov.co URL: $relatedUrl")
        }
    }
}

foreach ($city in $requiredCities) {
    if (-not $seenMunicipalities.Contains($city)) {
        $failures.Add("No municipal source is cataloged for $city.")
    }
}

if ($failures.Count -gt 0) {
    Write-Output 'Municipal catalog validation failed:'
    foreach ($failure in $failures) { Write-Output " - $failure" }
    throw ('Municipal catalog validation failed: ' + ($failures -join ' | '))
}

Write-Output "Municipal catalog validation passed: $($catalog.documents.Count) source records across $($seenMunicipalities.Count) municipalities."
