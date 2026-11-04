param(
    [string]$EnvironmentFile = (Join-Path $PSScriptRoot '..\.env.production'),
    [switch]$VerifyRunningStack,
    [switch]$RequireSevenCityCoverage,
    [switch]$RequireMunicipalDiscovery
)

$ErrorActionPreference = 'Stop'
$errors = [System.Collections.Generic.List[string]]::new()
$environmentPath = [IO.Path]::GetFullPath($EnvironmentFile)
$composePath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\docker-compose.production.yml'))
$settings = @{}

if (-not (Test-Path -LiteralPath $environmentPath -PathType Leaf)) {
    $errors.Add("Production environment file is missing: $environmentPath")
} else {
    foreach ($line in [IO.File]::ReadAllLines($environmentPath)) {
        $trimmed = $line.Trim()
        if ($trimmed -match '^(?<name>[A-Za-z_][A-Za-z0-9_]*)=(?<value>.*)$') {
            $settings[$Matches.name] = $Matches.value.Trim().Trim('"').Trim("'")
        }
    }
}

foreach ($name in @('DATABASE_PASSWORD', 'NORMBUILD_INGESTION_API_KEY')) {
    $value = $settings[$name]
    if ([string]::IsNullOrWhiteSpace($value) -or $value.Length -lt 32 -or $value.ToLowerInvariant().Contains('replace-')) {
        $errors.Add("$name must be replaced with a unique random secret of at least 32 characters.")
    }
}
if ($settings['DATABASE_PASSWORD'] -and $settings['NORMBUILD_INGESTION_API_KEY'] -and
    $settings['DATABASE_PASSWORD'] -ceq $settings['NORMBUILD_INGESTION_API_KEY']) {
    $errors.Add('Database and ingestion secrets must be different.')
}
if ($RequireMunicipalDiscovery -and [string]::IsNullOrWhiteSpace($settings['BRAVE_SEARCH_API_KEY'])) {
    $errors.Add('BRAVE_SEARCH_API_KEY is required when municipal source discovery is enabled.')
}

$docker = Get-Command docker -ErrorAction SilentlyContinue
if (-not $docker) {
    $errors.Add('Docker CLI is not installed or is not on PATH.')
} elseif (-not $errors.Count -or (Test-Path -LiteralPath $environmentPath -PathType Leaf)) {
    & docker info --format '{{.ServerVersion}}' *> $null
    if ($LASTEXITCODE -ne 0) {
        $errors.Add('Docker Engine is not reachable from the current Docker context.')
    } elseif (Test-Path -LiteralPath $environmentPath -PathType Leaf) {
        & docker compose --env-file $environmentPath -f $composePath config --quiet *> $null
        if ($LASTEXITCODE -ne 0) {
            $errors.Add('Production Docker Compose configuration is invalid. Check required variables and compose diagnostics.')
        }
    }
}

if ($VerifyRunningStack -and $docker) {
    & docker network inspect normbuild-edge *> $null
    if ($LASTEXITCODE -ne 0) {
        $errors.Add('The shared Docker network normbuild-edge does not exist.')
    }

    $apiPort = 8080
    if ($settings['API_HOST_PORT'] -match '^\d+$') {
        $apiPort = [int]$settings['API_HOST_PORT']
    }
    $apiBase = "http://127.0.0.1:$apiPort"
    try {
        $health = Invoke-RestMethod -Uri "$apiBase/actuator/health" -TimeoutSec 8
        if ($health.status -ne 'UP') {
            $errors.Add('The backend actuator health status is not UP.')
        }
    } catch {
        $errors.Add("The backend health endpoint is unavailable at $apiBase.")
    }

    $vectorOutput = & docker compose --env-file $environmentPath -f $composePath exec -T database psql -U normbuild -d normbuild -Atc "SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector');" 2>&1
    if ($LASTEXITCODE -ne 0 -or ($vectorOutput -join '').Trim() -ne 't') {
        $errors.Add('PostgreSQL is not ready or the vector extension is missing.')
    }

    $modelOutput = & docker compose --env-file $environmentPath -f $composePath exec -T ollama ollama list 2>&1
    $modelText = $modelOutput -join [Environment]::NewLine
    if ($LASTEXITCODE -ne 0 -or $modelText -notmatch 'llama3\.1' -or $modelText -notmatch 'nomic-embed-text') {
        $errors.Add('Ollama must have both llama3.1 and nomic-embed-text downloaded.')
    }

    if ($RequireSevenCityCoverage) {
        try {
            $coverage = Invoke-RestMethod -Uri "$apiBase/api/regulations/coverage" -TimeoutSec 15
            $requiredCities = @('Bogot' + [char]0x00E1 + ' D.C.', 'Pasto', 'Medell' + [char]0x00ED + 'n', 'Cali', 'Bucaramanga', 'Barranquilla', 'Cartagena')
            foreach ($city in $requiredCities) {
                $cityCoverage = $coverage | Where-Object { $_.jurisdiction -ceq $city } | Select-Object -First 1
                if (-not $cityCoverage -or $cityCoverage.officialSources -lt 1 -or $cityCoverage.indexedSections -lt 1) {
                    $errors.Add("No indexed official source is available for $city.")
                }
            }
        } catch {
            $errors.Add('Could not verify seven-city indexed coverage from the backend API.')
        }
    }
}

if ($errors.Count -gt 0) {
    Write-Output 'Production preflight failed:'
    foreach ($errorMessage in $errors) {
        Write-Output " - $errorMessage"
    }
    exit 1
}

Write-Output 'Production preflight passed.'
if (-not $VerifyRunningStack) {
    Write-Output 'Runtime health, pgvector, Ollama models and corpus coverage were not checked. Use -VerifyRunningStack after startup.'
}
if ($VerifyRunningStack -and -not $RequireSevenCityCoverage) {
    Write-Output 'Seven-city indexed source coverage was not required. Use -RequireSevenCityCoverage after legal review and ingestion.'
}
