# Account workspace design

The public checklist remains available without an account. A registered citizen can save completed requests and retrieve only their own history. Administrators can import reviewed official sources through `/api/account/admin/sources`. The existing ingestion API key remains available for operational scripts.

## Storage

`app_users` stores normalized email, a one-way password hash, and a server-assigned role. Public registration can only create `CITIZEN`. `user_sessions` stores a hash of a random session token, its owner, and expiry. `query_history` is owned by a user and retains the question, answer, mode, risk, and citations. Foreign keys remove sessions and history when an account is deleted.

## Browser boundary

The Vercel frontend proxies `/api/account` through its own origin. The server issues a Secure, HttpOnly, SameSite=Lax cookie with an eight-hour lifetime. Account mutations reject an Origin outside the configured frontend allowlist. No access token or password belongs in local storage, application logs, or client bundles.

Only `CITIZEN` accounts can register publicly. Promote a reviewed account to `ADMIN` through a private database operation; do not add an admin signup route or publish the ingestion key. Administrators should verify the source, jurisdiction, amendments, and legal applicability before import.

## Rollout gates

1. Verify the account tables and session cookie against the deployed PostgreSQL 16 and Vercel rewrite.
2. Verify login, logout, expiration, role checks, CSRF origin rejection, and history isolation against the deployed service.
3. Add a distributed registration rate limit and password reset before encouraging broad public account creation.
4. Add a retention policy and account deletion before storing long-term personal project descriptions.
