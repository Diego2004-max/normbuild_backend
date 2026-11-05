# Official source discovery and indexing

NormBuild can search for POT and urban-planning documents for municipalities
outside its indexed catalog. Discovery results are candidates, not legal
findings. The search API only returns HTTPS links hosted on Colombian `.gov.co`
domains whose title or summary indicates an urban-planning instrument.

## Configure live web search

The backend uses the Brave Search Web API. Create a server-side API key through
the Brave Search API account, then set `BRAVE_SEARCH_API_KEY` in the backend
environment. Never add this key to Vue, Vite variables, browser storage or Git.
The search endpoint returns a clear service-unavailable error when the key is
missing; it does not substitute sample results.

For local development, add `BRAVE_SEARCH_API_KEY=...` to the ignored backend
`.env` file and restart Spring Boot. For production, set the same variable in
the server's protected `.env.production` file or secret manager. The checked-in
environment examples intentionally leave this value blank.

The public endpoint is `POST /api/regulations/sources/search`:

```json
{"municipality":"Manizales"}
```

It targets Colombia and Spanish, returns at most eight distinct results, and
labels each result `REQUIRES_LEGAL_REVIEW`. Search snippets and official domain
checks do not establish whether an act remains in force or applies to a parcel.

## Review and index an official source

1. Open each official result and verify the issuing authority, adoption and
   publication, effective status, later amendments, judicial orders, annexes,
   and applicable jurisdiction. Check parcel maps separately where needed.
2. In the page, select **Revisar e incorporar fuente**, enter the exact act name,
   the legal code (for example, an agreement or decree), and the administrative
   ingestion key. Confirm the legal review only after completing step 1.
3. The backend re-fetches the URL itself. It accepts HTTPS `.gov.co` hosts and
   supports selectable-text PDFs up to 40 MB or HTML/XHTML pages up to 10 MB.
   Scanned PDFs need reviewed OCR before they can be imported. Navigation,
   scripts, styles, headers and footers are removed from HTML before indexing.
4. The endpoint is `POST /api/regulations/documents/from-official-url`; the
   former `/documents/pdf/from-official-url` route remains available for clients
   that have not yet migrated.
5. The backend extracts text, splits it into referenced sections, generates
   embeddings with `nomic-embed-text`, and stores sections and the official URL
   in PostgreSQL/pgvector. The jurisdiction becomes visible in the live
   coverage endpoint after indexing.

Indexing requires `NORMBUILD_INGESTION_API_KEY` in the backend environment.
The key typed into the browser is held only in page memory and sent over HTTPS
to the protected endpoint; it is cleared when the operation finishes. The
browser never writes it to local storage.

The importer cannot decide legal validity, reconcile every amendment, or
interpret municipal maps. Keep the confirmation step and source audit in place;
do not index a search result solely because it uses a government domain.
