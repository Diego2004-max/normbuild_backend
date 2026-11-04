$ErrorActionPreference = 'Stop'
$statePath = Join-Path $env:TEMP 'normbuild-ollama-cpu.json'

if (-not (Test-Path -LiteralPath $statePath -PathType Leaf)) {
    Write-Output 'No NormBuild CPU Ollama process is recorded.'
    return
}

$metadata = Get-Content -LiteralPath $statePath -Raw -Encoding utf8 | ConvertFrom-Json
$process = Get-Process -Id ([int]$metadata.processId) -ErrorAction SilentlyContinue
if ($null -eq $process) {
    Remove-Item -LiteralPath $statePath -Force
    Write-Output 'The NormBuild CPU Ollama process has already stopped.'
    return
}

$expectedPath = [IO.Path]::GetFullPath([string]$metadata.executable)
$actualPath = [IO.Path]::GetFullPath($process.Path)
$expectedStart = [DateTimeOffset]::Parse([string]$metadata.startedAtUtc).UtcDateTime
$actualStart = $process.StartTime.ToUniversalTime()
if (-not [string]::Equals($expectedPath, $actualPath, [StringComparison]::OrdinalIgnoreCase) -or
    [math]::Abs(($actualStart - $expectedStart).TotalSeconds) -gt 2) {
    throw "Recorded PID $($metadata.processId) no longer identifies the NormBuild Ollama process. Refusing to stop it."
}

Stop-Process -Id $process.Id -Force
Remove-Item -LiteralPath $statePath -Force
Write-Output "Stopped the NormBuild CPU Ollama process on port $($metadata.port)."
