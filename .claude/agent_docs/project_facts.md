# AIAE Presentation Builder — Project Facts

The rest of `.claude/` is the shared AiDigital engineering contract (rules, agent docs, skills),
kept generic on purpose. This file holds the facts that are specific to **this** repository, verified
against the code. When a shared doc says "discover the repository's …", the answer is here. When a
shared doc names infrastructure that this project does not have yet, the "Known gaps" section says
how to behave.

Verified against the code on 2026-11-11. If you find a fact here that no longer matches the code,
fix this file in the same change.

## What the product is

A report constructor for AI Digital account managers: it reads a campaign's media plan, Elevate
delivery data and optional paid-media exports, builds a reviewable Google Sheet, and generates
end-of-campaign (EOC) and end-of-month (EOM) Google Slides decks, with AI-written narrative copy.
Two deck designs exist: **Standard** and **MPO** (`TemplateVariant`).

## Backend

- Production package root: **`com.aidigital.reportconstructor`** (one root across all modules).
- Maven modules (`backend/pom.xml`), in build order: `domain`, `migrations`, `observability`,
  `event-logging-to-db-feature`, `service`, `external-services`, `application`.
- `backend/observability` (`com.aidigital.reportconstructor.observability.external`):
  `ExternalClientMetricsInterceptor` and `ExternalCallTimer`, both recording the
  `external.client.requests` timer tagged `client` / `operation` / `outcome`.
- Liquibase changelogs: `backend/migrations/src/main/resources/db/changelog`
  (master `db.changelog-master.xml`, change sets under `changes/`). The shared docs' `backend/db`
  path is the template default; in this project it is `backend/migrations`.
- Outbound integrations in `backend/external-services`, one package each:
  - `anthropic` — Claude (AI narrative copy, digests, prompt builders, grounding/figure check).
  - `google` — Sheets, Slides, Drive (sheet build, deck generation, charts, template handling).
  - `clerk` — Clerk OAuth token access.
  - `common.http` — `PooledRestClientFactory` (bounded Apache HttpClient 5 pool per client, explicit
    timeouts from `app.external.http.*`, metrics + Logbook interceptors). Anthropic gets one pooled
    client per per-call response timeout; Clerk uses a 15 s response timeout.
  - Google SDK calls run through `google.GoogleCallTimer` (directly or per attempt via
    `GoogleRequestRetrier`), tagged `google-sheets|slides|drive` and the SDK operation.
- HTTP logging (Logbook, inbound and outbound) is metadata-only by default
  (`app.logbook.include-bodies: false`); headers such as `Authorization` and `X-API-Key` are masked.
- OpenAPI contract: `backend/application/src/main/resources/api/v1/specs/openapi.yaml`; generated
  server sources under `backend/application/target/generated-sources` (never edit).
- Checkstyle runs in the Maven build (`backend/config/checkstyle.xml`): LineLength 120 (tab width 4),
  FileLength 2700. `ClaudeBatchPromptBuilder.java` sits at the FileLength limit — grow it only by
  moving code out, never by adding lines.
- Run Maven from `backend/` (Checkstyle resolves its suppressions file relative to the working
  directory).

## Frontend

- React + TypeScript + Vite under `frontend/`; API client boundary `frontend/src/shared/api`
  (generated types `frontend/src/shared/api/generated/schema.d.ts`, regenerate with
  `npm run generate:api`).
- Navigation shell: **top header**. `frontend/src/app/AppShell.tsx` renders
  `frontend/src/shared/ui/AppHeader.tsx` ("Elevate layout, no left sidebar") above a centered
  content area. Earlier versions of these docs claimed a left-nav shell under `features/layout`;
  that directory never existed in this repository. Preserve the header shell unless the product
  owner explicitly asks for a different navigation model.
- Feature areas: `frontend/src/features/report-constructor`, `my-reports`, `admin-dashboard`
  (`_template` is scaffold).
- Design tokens: `frontend/src/shared/ui/base/tokens.css` and
  `frontend/src/shared/ui/aidigital-tokens.css` (loaded from `app/AppLayout.tsx` / `main.tsx`).
  Token families actually defined here: `--primary`, `--background`, `--foreground`, `--accent*`,
  `--surface-*`, `--text-*`, `--border*`, `--radius-sm|md|lg|xl|full`, `--space-*`, `--status-*`,
  `--chart-*`, and the report-constructor palette `--rc-*`. The token tables in
  `frontend_style.md` describe the shared design system; use these real names in this project.
- Auth bootstrap: `GET /api/v1/auth/me`.

## Runtime

- Local dev: Vite on **5173** (`strictPort`), backend on **5000** (`scripts/local-dev-backend.sh`,
  `.replit`); Vite proxies the API to `BACKEND_DEV_PORT` (default 5000). `application.yml` falls back
  to 8080 only when `PORT` is unset.
- Full local verification: `scripts/local-verify.sh` (backend `mvn test`, frontend `npm ci`,
  `generate:api`, `typecheck`, `lint`, `build`).
- Deck/sheet template ids come from `application.yml` defaults, overridden locally by
  `backend/.env.local` (never print that file: it holds the Google service-account key).

## Tests

- Backend: JUnit 5 + Mockito unit tests per module, MVC/controller tests in `application`.
  There is **no dedicated `*IT` integration-test layer**.
- Frontend: Vitest + Testing Library (`npm test`), `npm run typecheck`, `npm run lint`, `npm run build`.
- Lint: ESLint 9 flat config `frontend/eslint.config.js` (typescript-eslint, local rule
  `project-rules/import-section-order` in `frontend/eslint-rules/`, and a `no-restricted-syntax` ban on
  inline `interface`/`type` and top-level `const` in `src/**/*.tsx`, tests included — test mocks and
  fixtures live in `features/*/testing/*.ts`). Zero errors is the baseline; never disable rules.
- Pre-commit: Husky 9 (`.husky/pre-commit` runs `npm --prefix frontend run lint`), installed by the
  frontend `prepare` script (`scripts/prepare-husky.mjs`, sets `core.hooksPath=.husky/_`).
- Frontend CSS is `rem`-only; hex literals appear only in the token definition files.

## Known gaps vs the shared contract

These shared rules describe infrastructure or conventions this project does not have yet. Apply the
rule to **new** code where it can be applied; do not retrofit existing code or create the missing
infrastructure as a side effect of unrelated work — raise it as its own task.

| Shared rule | State in this project | How to behave |
|---|---|---|
| Every Liquibase `changeSet` declares `preConditions`; `scripts/verify-gates.sh` checks it | `0001-usage-events.xml` and `0002-report-jobs.xml` have none; there is no `verify-gates.sh` (the closest is `scripts/local-verify.sh`). | Every **new** changeSet declares `preConditions`. Never rewrite applied changelogs. |
| BigQuery query rules (`13-bigquery.md`) | No BigQuery SDK code; delivery data arrives through exported Google Sheets (Elevate). | Applies only if BigQuery access is added. |
| GSD workflow (`skill-selection.md`) | GSD is not installed. | Use `task-workflow` and the focused skills; install GSD only on explicit request. |
