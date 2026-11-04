param(
    [string]$BaseUrl = 'http://127.0.0.1:11435',
    [ValidateRange(30, 600)]
    [int]$TimeoutSeconds = 300
)

$ErrorActionPreference = 'Stop'
$embeddingRequest = @{
    model = 'nomic-embed-text'
    prompt = 'vivienda residencial lote urbano índice de ocupación'
} | ConvertTo-Json
$embedding = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/embeddings" -ContentType 'application/json' `
    -Body $embeddingRequest -TimeoutSec $TimeoutSeconds
if ($embedding.embedding.Count -ne 768) {
    throw "Expected a 768-dimensional embedding; Ollama returned $($embedding.embedding.Count)."
}

$generationRequest = @{
    model = 'llama3.1'
    prompt = 'Responde en español con una frase breve: ¿qué dato del predio se necesita para confirmar las normas de construcción aplicables?'
    stream = $false
    options = @{
        num_predict = 96
        temperature = 0.1
    }
} | ConvertTo-Json -Depth 4
$generation = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/generate" -ContentType 'application/json' `
    -Body $generationRequest -TimeoutSec $TimeoutSeconds
if ([string]::IsNullOrWhiteSpace($generation.response) -or $generation.done -ne $true -or $generation.done_reason -eq 'length') {
    throw 'The LLM did not return a complete response. Check the Ollama server logs and model configuration.'
}

[PSCustomObject]@{
    EmbeddingModel = 'nomic-embed-text'
    EmbeddingDimensions = $embedding.embedding.Count
    LanguageModel = $generation.model
    GenerationComplete = $generation.done
    OutputTokens = $generation.eval_count
    DurationSeconds = [math]::Round($generation.total_duration / 1e9, 1)
    Response = $generation.response.Trim()
} | Format-List
