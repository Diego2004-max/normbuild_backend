# NormBuild Backend

Spring Boot API for project analysis and source-grounded regulatory guidance.

## Requirements

- Java 21
- Maven 3.9 or newer
- Docker Desktop
- Ollama with `llama3.1` and `nomic-embed-text`

## Local setup

1. Copy `.env.example` to `.env` and set private database and ingestion secrets.
2. Start PostgreSQL with `docker compose up -d database`.
3. Pull the local models with `ollama pull llama3.1` and `ollama pull nomic-embed-text`.
4. Start the API with `mvn spring-boot:run`.
5. Open `http://localhost:8080/actuator/health` to inspect service health.

## Single-host production deployment

The API, PostgreSQL/pgvector and Ollama can run on a Linux Docker host. The
frontend is deployed from its own repository and shares the `normbuild-edge`
Docker network with this API. Create that network once with
`docker network create normbuild-edge`, copy `.env.production.example` to
`.env.production`, set unique long random secrets, and start the backend from
this repository:

```powershell
docker compose --env-file .env.production -f docker-compose.production.yml up -d --build
```

The first startup downloads `llama3.1` and `nomic-embed-text`; this requires
several gigabytes of disk and can take time. Ollama runs on CPU by default; a
GPU-enabled Linux host and compatible NVIDIA Container Toolkit can improve
generation latency. Use a host with at least 16 GB RAM for this local 8B model
stack. PostgreSQL records and Ollama models use persistent Docker volumes.
Back up `normbuild-production-postgres` and protect `.env.production`; do not
commit production secrets.

See [deployment prerequisites and verification](docs/deployment.md) before
opening the application to the internet. A deployment is not complete until
the health checks pass, the seven cities' current source sets are imported, and
a real cited request has been verified against that corpus.

The API does not seed paraphrased municipal rules. Only source documents that have
been reviewed and ingested are eligible for retrieval and citation. Document
ingestion requires the `X-NormBuild-Ingestion-Key` header, a reviewed HTTPS source
URL on an official `.gov.co` host, and is disabled until
`NORMBUILD_INGESTION_API_KEY` is configured.

## AI request flow

The API extracts project measurements, computes proposed occupancy and construction
indices, creates an embedding with Ollama, performs jurisdiction-scoped cosine
search in PostgreSQL/pgvector, and supplies retrieved source text to the local LLM.
The SSE endpoint emits analysis progress and complete source references while the
LLM generates its response. Missing municipal documents return an explicit
no-context result.

## API

- `POST /api/regulations/compliance-checklist/stream`: progressive RAG checklist.
- `POST /api/regulations/compliance-checklist`: JSON RAG checklist.
- `POST /api/regulations/documents`: asynchronously index reviewed source text.
- `POST /api/regulations/documents/pdf`: extract and index reviewed PDF sources in bounded, overlapping chunks.
- `GET /actuator/health`: service health.
- `GET /api/regulations/coverage`: actual indexed official source counts by city.

To import an official PDF, download it from the municipality's document repository,
set `NORMBUILD_INGESTION_API_KEY`, and run `scripts/ingest-official-pdf.ps1` with
the file path and the exact HTTPS `.gov.co` URL. Scanned PDFs without selectable
text are rejected; OCR review must be performed before ingestion. Re-importing
the same source sections does not create duplicate vector records.

Request and response contracts are maintained in the DTO records. User-facing
messages are Spanish; identifiers, comments and commit messages are English.
