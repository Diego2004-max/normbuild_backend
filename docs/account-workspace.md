# Account workspace design

The public checklist remains available without an account. A registered citizen can save completed requests and retrieve only their own history. Administrators can manage reviewed official sources through separate authenticated operations. The existing ingestion API key remains in place until the admin endpoints are protected and tested.

## Storage

`app_users` stores normalized email, a one-way password hash, and a server-assigned role. Public registration can only create `CITIZEN`. `user_sessions` stores a hash of a random session token, its owner, and expiry. `query_history` is owned by a user and retains the question, answer, mode, risk, and citations. Foreign keys remove sessions and history when an account is deleted.

## Browser boundary

The frontend must proxy `/api` through its own origin before cookie sessions are enabled. The server will issue a Secure, HttpOnly, SameSite=Lax cookie with a bounded lifetime. State-changing requests require CSRF protection. No access token or password belongs in local storage, application logs, or client bundles.

## Rollout gates

1. Verify the database migration against PostgreSQL 16.
2. Add password hashing, generic login errors, registration throttling, and session rotation.
3. Enforce citizen ownership on every history query and restrict administrative operations to `ADMIN`.
4. Add same-origin proxying, Spanish account forms, accessible validation, and history controls.
5. Verify login, logout, expiration, authorization, CSRF, and history isolation in a deployed environment before exposing registration.
