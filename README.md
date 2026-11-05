# NormBuild Backend

Spring Boot API for project analysis and source-grounded regulatory guidance.

## Requirements

- Java 21
- Maven 3.9 or newer
- Docker Desktop
- Ollama with `llama3.1` and `nomic-embed-text`

## Local setup

1. Copy `.env.example` to `.env` and set private database and ingestion secrets.
   Set `BRAVE_SEARCH_API_KEY` in this local file to enable live municipal
   source discovery. Git and Docker exclude `.env`; commit only the blank
   `.env.example` template, never the local values.
2. Start the isolated PostgreSQL/pgvector container with `docker compose up -d database`. It binds to `127.0.0.1:5435`, so it avoids the local PostgreSQL services already using ports 5432 and 5433.
3. Pull the local models with `ollama pull llama3.1` and `ollama pull nomic-embed-text`.
4. Run `scripts/start-local-ai.ps1`. It starts a separate CPU AVX2 Ollama endpoint on port 11435, avoiding CUDA runner failures while leaving the regular Ollama server on port 11434 untouched.
5. Start the API with `mvn spring-boot:run`.
6. Open `http://localhost:8080/actuator/health` to inspect service health.

The CPU inference check is available with `scripts/verify-local-ai.ps1` and the
temporary Ollama endpoint can be stopped with `scripts/stop-local-ai.ps1`.

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
opening the application to the internet. Verify the five base cities' indexed
corpus where available and confirm that live official-source research is enabled
for any municipality without local documents.

The API does not seed paraphrased municipal rules. Only source documents that have
been reviewed and ingested are eligible for retrieval and citation. Document
ingestion requires the `X-NormBuild-Ingestion-Key` header, a reviewed HTTPS source
URL on an official `.gov.co` host, and is disabled until
`NORMBUILD_INGESTION_API_KEY` is configured.

## AI request flow

The API extracts project measurements, computes proposed occupancy and construction
indices, creates an embedding with Ollama, and performs jurisdiction-scoped cosine
search in PostgreSQL/pgvector. If no local municipal source is indexed, it searches
Brave LLM Context using only the city name and generic urban-planning terms, keeps
HTTPS `.gov.co` results, and asks the local LLM to draft a preliminary answer from
those extracts. The project description is not sent to Brave. Live-web citations
are visibly marked as unverified and are not added to the vector database.

## API

- `POST /api/regulations/compliance-checklist/stream`: progressive RAG checklist.
- `POST /api/regulations/compliance-checklist`: JSON RAG checklist.
- `POST /api/regulations/documents`: asynchronously index reviewed source text.
- `POST /api/regulations/documents/pdf`: extract and index reviewed PDF sources in bounded, overlapping chunks.
- `GET /actuator/health`: service health.
- `GET /api/regulations/coverage`: actual indexed official source counts by city.
- `POST /api/regulations/sources/search`: discover live Colombian official POT candidates using `BRAVE_SEARCH_API_KEY`.
- `POST /api/regulations/documents/from-official-url`: protected download, review-confirmed PDF/HTML parsing and vector indexing.

For the new-city discovery, provider setup and human review workflow, see
[`docs/official-source-discovery.md`](docs/official-source-discovery.md).

To import an official PDF, download it from the municipality's document repository,
set `NORMBUILD_INGESTION_API_KEY`, and run `scripts/ingest-official-pdf.ps1` with
the file path and the exact HTTPS `.gov.co` URL. Scanned PDFs without selectable
text are rejected; OCR review must be performed before ingestion. Re-importing
the same source sections does not create duplicate vector records.

Request and response contracts are maintained in the DTO records. User-facing
messages are Spanish; identifiers, comments and commit messages are English.
## Deployment preflight

Copy .env.production.example to .env.production, set distinct strong secrets,
then verify configuration before startup:

    .\scripts\verify-production.ps1 -EnvironmentFile .\.env.production -RequireMunicipalDiscovery

After the backend stack is running, check API health, PostgreSQL/pgvector and
both Ollama models:

    .\scripts\verify-production.ps1 -EnvironmentFile .\.env.production -VerifyRunningStack

After reviewing and importing current official sources, require the five base
cities to have indexed source sections:

    .\scripts\verify-production.ps1 -EnvironmentFile .\.env.production -VerifyRunningStack -RequireFiveCityCoverage

The coverage check verifies indexed source counts. It does not certify legal
currency, amendment completeness or parcel-level applicability.
