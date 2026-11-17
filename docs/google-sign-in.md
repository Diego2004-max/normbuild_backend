# Google sign-in setup

NormBuild uses Google Identity Services to obtain a Google ID token in the browser. The backend verifies the signature, audience, expiry, issuer, and verified email before creating its own private session. Google subject identifiers are unique and never inferred from email alone. Password accounts are not linked automatically.

1. In Google Cloud Console, configure the OAuth consent screen for the intended audience and create an OAuth 2.0 **Web application** client.
2. Add `https://normbuild-frontend.vercel.app` to **Authorized JavaScript origins**. For local development, add `http://localhost:5173` only if that origin is actually used. The browser callback flow does not need a redirect URI.
3. Set `GOOGLE_OAUTH_CLIENT_ID` to the Web client ID in the Render backend environment. A client ID is public configuration; do not put a client secret in the frontend.
4. Redeploy the Render service, then check `/api/account/google/config` through the Vercel domain. It should return the configured client ID.
5. Test a new Google account on `/cuenta`, then confirm `/api/account/me`, private history, logout, and a second Google login.

Google accounts and password accounts with the same email are kept separate until an explicit account-linking flow exists. Since email is unique, Google sign-in returns a conflict when a password account already uses the address. This prevents an unverified password registration from taking over a Google identity or vice versa.
