# AIAE Presentation Builder

Turns a campaign's media plan, Elevate delivery data and optional paid-media
exports into a reviewable Google Sheet and end-of-campaign / end-of-month
Google Slides decks (Standard and MPO designs), with AI-written narrative copy.

- Architecture and project facts: [`docs/architecture-overview.md`](docs/architecture-overview.md)
- AIAE convergence status: [`docs/aiae-migration-plan.md`](docs/aiae-migration-plan.md)
- User guide for EOM reports: [`EOM-guide.md`](EOM-guide.md)
- AI-assisted development workflow: [`AI-DEVELOPMENT-GUIDE.md`](AI-DEVELOPMENT-GUIDE.md)

## What this is

An internal tool for AI Digital account managers. A user signs in with Google
(Clerk SSO, company domain only), points the wizard at the campaign's media
plan and delivery sheets, reviews the matched line items and the generated
review sheet, and gets a Google Slides deck in their Drive. Reports can be
resumed from the review sheet and are listed under **My Reports**. Admins see
usage, token cost and failed jobs on the admin dashboard.

## How to run

### On Replit
1. Set the Clerk secrets (`CLERK_SECRET_KEY`, `VITE_CLERK_PUBLISHABLE_KEY`,
   issuer / authorized-party settings), the Anthropic key, and the Google
   service-account credentials in Replit Secrets.
2. Click **Run**: backend on 5000, Vite dev server on 5173.
3. Deployment is a **Reserved VM** (`gce`), external port 80 → 5000.

### On a local developer machine
1. Put local overrides (template ids, Google service account) in
   `backend/.env.local` — never commit or print that file.
2. `bash scripts/local-dev-backend.sh` (Spring Boot on 5000).
3. `bash scripts/local-dev-frontend.sh` (Vite on 5173, proxies the API to 5000).
4. Before pushing: `bash scripts/local-verify.sh` (build, tests, lint, then the
   report-only AIAE gates).

DEV and PROD run on AWS through GitHub Actions and GitOps; see
`.github/workflows/` and `docs/architecture-overview.md`.

## API

Base URLs:
- Local backend: `http://localhost:5000/api/v1`
- Replit / deployment: `https://<host>/api/v1`

Documentation:
- Swagger UI: `/swagger-ui/index.html`
- OpenAPI YAML: `/api/v1/specs/openapi.yaml`
  (source: `backend/application/src/main/resources/api/v1/specs/openapi.yaml`)

Main endpoints (all require a Clerk Bearer JWT; `admin/*` also requires admin access):

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/auth/me` | Current authenticated user |
| `POST` | `/api/v1/report-jobs` | Enqueue an async report-generation job |
| `GET` | `/api/v1/report-jobs/{jobId}` | Poll a report job |
| `GET` | `/api/v1/reports` | The caller's report history |
| `POST` | `/api/v1/reports/adopt-sheet` | Register a self-filled workbook as a resumable draft |
| `GET` | `/api/v1/admin/stats` | Team-wide statistics and technical health |
