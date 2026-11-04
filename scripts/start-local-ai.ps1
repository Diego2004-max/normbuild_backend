param(
    [ValidateRange(1024, 65535)]
    [int]$Port = 11435,
    [string]$OllamaPath = (Join-Path $env:LOCALAPPDATA 'Programs\Ollama\ollama.exe'),
    [string]$ModelDirectory = (Join-Path $env:USERPROFILE '.ollama\models'),
    [string]$ModelSourceUrl = 'http://127.0.0.1:11434'
)

$ErrorActionPreference = 'Stop'
$baseUrl = "http://127.0.0.1:$Port"
$statePath = Join-Path $env:TEMP 'normbuild-ollama-cpu.json'
$logDirectory = Join-Path $env:TEMP 'normbuild-ollama-cpu'

if (-not (Test-Path -LiteralPath $OllamaPath -PathType Leaf)) {
    throw "Ollama executable was not found at '$OllamaPath'. Pass -OllamaPath to its installed executable."
}
if (-not (Test-Path -LiteralPath $ModelDirectory -PathType Container)) {
    throw "Ollama model directory was not found at '$ModelDirectory'. Pass -ModelDirectory to the installed models directory."
}

try {
    Invoke-RestMethod -Uri "$baseUrl/api/version" -TimeoutSec 2 | Out-Null
    throw "Port $Port already has an Ollama server. Stop that server or choose another port."
} catch {
    if ($_.Exception.Message -like "Port $Port already has an Ollama server*") {
        throw
    }
}

$sourceTags = Invoke-RestMethod -Uri "$ModelSourceUrl/api/tags" -TimeoutSec 10
$modelNames = @($sourceTags.models | ForEach-Object { $_.name })
foreach ($requiredModel in @('llama3.1', 'nomic-embed-text')) {
    if (-not ($modelNames | Where-Object { $_ -like "$requiredModel*" })) {
        throw "Model '$requiredModel' is missing from Ollama at $ModelSourceUrl. Pull it with 'ollama pull $requiredModel' first."
    }
}

New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
$oldEnvironment = @{
    OLLAMA_HOST = $env:OLLAMA_HOST
    OLLAMA_MODELS = $env:OLLAMA_MODELS
    OLLAMA_LLM_LIBRARY = $env:OLLAMA_LLM_LIBRARY
}
$serverProcess = $null
try {
    $env:OLLAMA_HOST = "127.0.0.1:$Port"
    $env:OLLAMA_MODELS = $ModelDirectory
    $env:OLLAMA_LLM_LIBRARY = 'cpu_avx2'
    $serverProcess = Start-Process -FilePath $OllamaPath -ArgumentList 'serve' -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $logDirectory 'server.stdout.log') `
        -RedirectStandardError (Join-Path $logDirectory 'server.stderr.log')
} finally {
    foreach ($name in $oldEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $oldEnvironment[$name], 'Process')
    }
}

$metadata = [PSCustomObject]@{
    processId = $serverProcess.Id
    executable = [IO.Path]::GetFullPath($OllamaPath)
    startedAtUtc = $serverProcess.StartTime.ToUniversalTime().ToString('o')
    port = $Port
}
$metadata | ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding utf8

$deadline = (Get-Date).AddSeconds(45)
$ready = $false
do {
    if ($serverProcess.HasExited) {
        break
    }
    try {
        $version = Invoke-RestMethod -Uri "$baseUrl/api/version" -TimeoutSec 2
        $ready = -not [string]::IsNullOrWhiteSpace($version.version)
    } catch {
        Start-Sleep -Milliseconds 500
    }
} while (-not $ready -and (Get-Date) -lt $deadline)

if (-not $ready) {
    if (-not $serverProcess.HasExited) {
        Stop-Process -Id $serverProcess.Id -Force
    }
    Remove-Item -LiteralPath $statePath -Force -ErrorAction SilentlyContinue
    $errorLog = Join-Path $logDirectory 'server.stderr.log'
    if (Test-Path -LiteralPath $errorLog) {
        Get-Content -LiteralPath $errorLog -Tail 30
    }
    throw "The CPU Ollama server did not become ready on port $Port."
}

$activeTags = Invoke-RestMethod -Uri "$baseUrl/api/tags" -TimeoutSec 10
$activeNames = @($activeTags.models | ForEach-Object { $_.name })
foreach ($requiredModel in @('llama3.1', 'nomic-embed-text')) {
    if (-not ($activeNames | Where-Object { $_ -like "$requiredModel*" })) {
        Stop-Process -Id $serverProcess.Id -Force
        Remove-Item -LiteralPath $statePath -Force -ErrorAction SilentlyContinue
        throw "Model '$requiredModel' is not visible from the CPU Ollama server. Check the model directory."
    }
}

Write-Output "CPU AVX2 Ollama is ready at $baseUrl (PID $($serverProcess.Id))."
Write-Output 'Run scripts/verify-local-ai.ps1 to test real embeddings and generation.'
