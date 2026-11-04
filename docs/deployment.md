# Production Deployment

## Required infrastructure

- A Linux VPS or cloud VM with Docker Engine and the Docker Compose plugin.
- A public DNS name resolving to that host and inbound ports 80 and 443 for
  Caddy-managed TLS certificates.
- At least 16 GB RAM recommended for PostgreSQL and Ollama with `llama3.1` 8B;
  a compatible GPU can reduce model latency. Allow several GB of persistent disk
  for the model downloads and a separately sized database volume.
- Persistent storage and scheduled, tested backups for the PostgreSQL volume.
- Separate, long random values for `DATABASE_PASSWORD` and
  `NORMBUILD_INGESTION_API_KEY`, stored outside Git.
- A Brave Search API key in `BRAVE_SEARCH_API_KEY` to enable the live search for
  official sources in additional municipalities. Without it, the core assistant
  runs but city discovery explicitly reports that search is unavailable.

PostgreSQL with pgvector is required. The production compose file provisions it
with a persistent named volume. Ollama and both model packages are also
provisioned by compose; first boot downloads the model files. The catalog
includes official city POT baselines, the national Decree 1077 snapshot and
selected NSR-10 instruments. Download them with
`scripts/download-and-import-municipal-documents.ps1`; the catalog is not a
complete municipal amendments or map corpus. Each document must be checked for
current legal status before import. Database records, models and documents are
three distinct things; a running app does not mean the legal corpus has been
populated.

## Deploy order

1. Install Docker Engine and the Compose plugin on the server; configure DNS and
   the firewall for ports 80 and 443.
2. Clone the backend repository. Create the shared network with
   `docker network create normbuild-edge`.
3. Copy `.env.production.example` to `.env.production`, replace both example
   secrets with unique random secrets, and restrict file access to the deploy
   account. Start the backend:

   ```powershell
   docker compose --env-file .env.production -f docker-compose.production.yml up -d --build
   ```

4. Wait for `database`, `ollama`, `ollama-models` and `api` health/status. Verify
   API health locally at `http://127.0.0.1:8080/actuator/health`.
5. Clone the frontend repository on the same host. Set `PUBLIC_DOMAIN` to the
   DNS name and ensure `NORMBUILD_BACKEND_URL` is `http://normbuild-api:8080`.
   Start its compose file. Caddy obtains a public certificate when DNS and
   inbound ports are correct.
6. Download the catalogued official PDFs, review the listed amendments,
   and import only after confirming current legal status. Confirm the actual
   source counts at `/api/regulations/coverage`.
7. Submit one real checklist request per city, confirm citations open the exact
   official source, and verify streamed progress and model response.
8. Configure monitoring, disk alerts, scheduled off-host database backups, and
   a tested restore procedure before production use.

## Operational limits

- The initial catalog is a set of official base POT documents, not a complete
  consolidated legal corpus. Current amendments, parcel-specific maps, overlays,
  environmental or risk restrictions, and licensing documents can change the
  answer. The municipality's official parcel consultation and a licensed
  professional remain necessary for a project decision.
- Ollama model generation is compute-intensive. CPU deployments can be slow;
  verify expected concurrency and latency on the actual host before advertising
  a service level.
- This single-host compose configuration is an operational baseline. A managed
  production service should move secrets to a secret manager, use a managed
  PostgreSQL service that explicitly supports pgvector, and monitor/restore its
  backups. No cloud provider account or DNS domain is included in the source
  repositories.
