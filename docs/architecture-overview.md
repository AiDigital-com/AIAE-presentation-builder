# AIAE Presentation Builder Architecture Overview

This is the canonical description of the product's implemented architecture and
the home of every fact that is specific to this repository. `CLAUDE.md` and
`.claude/` are the shared AiDigital engineering contract, installed and
checksummed by the AIAE template (`.claude/.aiae-fixtures-manifest`) — **do not
edit them in this repository**; put project facts and project-specific
constraints here instead. When a shared doc says "discover the repository's …",
the answer is in this file.

Update this file in the same change whenever a runtime boundary, module,
data flow, integration, deployment model, or cross-cutting concern changes.

## Document status

- Owner: AIAE Presentation Builder engineering team
- Lifecycle phase: MVP
- Last verified against: branch `PDI_240`, 2026-11-13
- Verification evidence: `bash scripts/local-verify.sh`, `bash scripts/run-gates.sh`
- Cache status: disabled
- MVP usage telemetry: enabled during MVP

"Cache status: disabled" — no application cache of any kind: no
`backend/cache-management` module, no Spring cache, no Hibernate L2. See
[Caching and consistency](#caching-and-consistency).

## Product and system context

A report constructor for AI Digital account managers. It reads a campaign's
media plan, Elevate delivery data and optional paid-media exports, builds a
reviewable Google Sheet, and generates end-of-campaign (EOC) and end-of-month
(EOM) Google Slides decks with AI-written narrative copy. Two deck designs
exist: **Standard** and **MPO** (`TemplateVariant`).

```mermaid
flowchart LR
    user["Account manager"] -->|HTTPS| app["Presentation Builder"]
    admin["Admin"] -->|HTTPS| app
    app -->|OIDC / JWT| clerk["Clerk with Google SSO"]
    app -->|SQL| postgres["PostgreSQL"]
    app -->|Sheets / Slides / Drive SDK| google["Google Workspace"]
    app -->|Messages API| anthropic["Anthropic Claude"]
```

The application is a React single-page application backed by a Spring Boot
API. The browser uses `BrowserRouter`; application routes resolve through the
SPA fallback (`SpaFallbackController`) when the built frontend is served by
Spring Boot.

## Product-specific evidence

- Product capability: Generate EOC/EOM Google Slides decks (Standard and MPO designs) and a reviewable Google Sheet from a campaign media plan and delivery data, with Claude-written narrative copy.
- Primary users: AI Digital account managers; admins use the usage and failure dashboard.
- Primary production flow: The user selects source sheets, the backend reads and matches line items, an async report job builds the review sheet and deck, and the user opens or resumes the result from My Reports.
- Evidence path: `backend/service/src/main/java/com/aidigital/reportconstructor/service/reports/services/impl/ReportGenerationServiceImpl.java`
- Evidence path: `backend/application/src/main/java/com/aidigital/reportconstructor/controllers/ReportJobsController.java`
- Evidence path: `frontend/src/features/report-constructor`

## Runtime and deployment

| Environment | Frontend | Backend | Data |
|---|---|---|---|
| Local development | Vite on 5173 (`strictPort`), proxies the API to `BACKEND_DEV_PORT` (default 5000) | Spring Boot on 5000 (`scripts/local-dev-backend.sh`); `application.yml` falls back to 8080 only when `PORT` is unset | Local PostgreSQL |
| Replit development | Vite dev server (`scripts/replit-dev-frontend.sh`) | Spring Boot on 5000 (`scripts/replit-dev-backend.sh`, profile `replit`) | Replit PostgreSQL |
| Replit deployment | Built SPA served by Spring Boot | Reserved VM (`gce`), external port 80 → 5000 | Replit PostgreSQL |
| DEV / PROD (AWS) | Built SPA served by Spring Boot | Container image in ECR, Helm chart `aiae-presentation-builder-api`, published through GitOps (`scripts/gitops-publish.sh`) | Managed PostgreSQL |

- DEV deploys on push to the numbered release branch that equals the
  `DEV_DEPLOY_BRANCH` environment variable (`.github/workflows/deploy-dev.yml`).
  PROD is a manual `release-prod.yml` dispatch whose `release_version` must
  equal the branch; `rollback-prod.yml` reverts.
- Deck/sheet template ids come from `application.yml` defaults, overridden
  locally by `backend/.env.local`. Never print that file: it holds the Google
  service-account key.
- Evidence: `.replit`
- Evidence: `.github/workflows/deploy-dev.yml`

## Repository and module boundaries

Production package root: **`com.aidigital.reportconstructor`** — one root
across all modules. Maven modules (`backend/pom.xml`), in build order:

| Area | Responsibility | May depend on |
|---|---|---|
| `frontend/` | React UI, routing, Clerk auth integration, typed API consumption | Generated OpenAPI types and the shared API client (`frontend/src/shared/api`) |
| `backend/domain` | JPA entities and repositories (`ReportJobEntity`, `AdminUserEntity`, `ClaudeUsageEventEntity`, `UsageDailyEntity`) | Persistence APIs only |
| `backend/migrations` | Liquibase changelogs | No production Java modules |
| `backend/observability` | `ExternalClientMetricsInterceptor` and `ExternalCallTimer` (`observability.external`), recording `external.client.requests` tagged `client` / `operation` / `outcome` | Metrics APIs only |
| `backend/event-logging-to-db-feature` | Temporary MVP feedback telemetry (`usage_events`); removed at engineering handoff | Application service annotations and PostgreSQL during MVP only |
| `backend/service` | Report engine, sheet/deck orchestration, admin statistics, entity services; the Claude prompt and reply logic (`reports.claude`); the adapters that implement its ports over the provider clients (`integration`) | `domain`, `external-services`, `event-logging-to-db-feature` |
| `backend/external-services` | Provider clients only: `anthropic`, `google`, `clerk`, `common.http`. A leaf — imports nothing from `service` or `domain` | `observability` |
| `backend/application` | Runtime composition, security, OpenAPI controllers, exception translation, Logbook, SPA hosting | `service`, `domain` (compile); `observability`, `migrations` (runtime). Never `external-services` directly |

**Module direction (AIAE standard; decision D6 resolved 2026-11-12).** `service → external-services`, and
`external-services` is a leaf. Each provider follows the onboarding-platform shape: a client interface in
provider terms, its real and stub implementations, Google/Anthropic/Clerk-owned `model/` records and
exceptions, and its own configuration. The service owns every business rule; `service.integration` holds the
classes that implement a service port on top of a provider client — they map types, translate failures into
`AppException`, and are the only service classes that touch provider types.

- **Clerk** — `ClerkClient.googleOAuthAccessTokens`; `integration.ClerkUserGoogleTokenProvider` implements
  `UserGoogleTokenProvider`. Both exist only when `app.clerk.secret-key` is set; otherwise callers use the
  Google service account.
- **Google Drive** — `google.drive` (`DriveCopier`, `DriveSharer`, `ChartFileSharer`, `DriveShareRecipients`).
  Who gets auto-shared comes from the Drive-owned `DriveShareAudience`, implemented by
  `integration.AdminDriveShareAudience` over `AdminEmailDirectory`.
- **Google Sheets reads** — `google.sheets.SheetsClient.readTab` (real `SheetsClientImpl` when Google credentials
  exist, `StubSheetsClient` otherwise); `integration.SheetQueryServiceImpl` implements `SheetQueryService`
  (URL validation, `SheetData` shaping, user-facing errors via `integration.SheetsFailureTranslator`).
- **Google Slides** — `google.slides.SlidesDeckClient` (12 deck operations; `RealSlidesProvider` /
  `StubSlidesProvider`), sharing the deck vocabulary in `google.deck` (`DeckBreakdown`, `DeckTemplate`,
  `DeckMarkers`, paid-slide types — copies of the service's, pinned by `DeckModelMapperTest`).
  `integration.SlidesProviderBridge` implements `SlidesProvider` and applies the service's
  `BreakdownThoughtsGate`.
- **Workbook writer** — `google.sheets.SheetWorkbookClient` (`RealSheetDeckProvider` / `StubSheetDeckProvider`,
  tables read back as `google.sheets.model.Workbook*`); `integration.SheetDeckProviderBridge` implements
  `SheetDeckProvider`.
- **Charts and pacing** — the service pivots the BigQuery export (`reports.engine.TacticPivotAssembler` over
  `ChartPivot` and `TacticLineItemGrouper`); the Google code only draws or writes the pivots it is given
  (`google.deck.model.TacticPacing`). `google.charts.SlidesChartClient` (`RealChartProvider` /
  `StubChartProvider`); `integration.ChartProviderBridge` implements `ChartProvider`.
- **Anthropic** — `anthropic.AnthropicClient` (`AnthropicMessagesClient`: auth, pooling, concurrency, retries,
  truncation salvage, JSON repair) reports usage and rejected replies through `AnthropicCallListener`
  (`integration.ClaudeCallRecorder` → `ClaudeUsageTracker`, `ClaudeFailureLog`). The prompts and reply checks
  are service code in `reports.claude` (`RealClaudeClient`, the prompt builders, `ClaudeResponseNormalizer`,
  `ClaudePromptProperties`), reaching the model only through the port `ClaudeTransport`, implemented by
  `integration.ClaudeTransportBridge`.

Where the service and a provider share a vocabulary (deck breakdowns, template variants, paid tables, chart
series, pacing pivots, workbook tables), the provider keeps its own copy and `service.integration` maps by
name; the `*ModelMapperTest`s fail when the two drift apart.

Controllers implement generated OpenAPI interfaces and delegate to services.
Repositories are accessed only through their paired entity services.

Frontend layout:

- Navigation shell is a **top header**: `frontend/src/app/AppShell.tsx` renders
  `frontend/src/shared/ui/AppHeader.tsx` ("Elevate layout, no left sidebar")
  above a centered content area. There is no left-nav shell and no
  `features/layout`; preserve the header unless the product owner asks
  otherwise.
- Feature areas: `frontend/src/features/report-constructor`, `my-reports`,
  `admin-dashboard` (`_template` is scaffold).
- Design tokens: `frontend/src/shared/ui/base/tokens.css` and
  `frontend/src/shared/ui/aidigital-tokens.css` (loaded from
  `app/AppLayout.tsx` / `main.tsx`). Token families defined here: `--primary`,
  `--background`, `--foreground`, `--accent*`, `--surface-*`, `--text-*`,
  `--border*`, `--radius-sm|md|lg|xl|full`, `--space-*`, `--status-*`,
  `--chart-*`, and the report-constructor palette `--rc-*`. Where the shared
  `frontend_style.md` token tables use other names, use these real names.
- Frontend CSS is `rem`-only and has no hex literals: the token files define colours as
  `rgb(r g b)`, components use the tokens. `shared/ui/base/reset.css` makes every `<button>`
  centred `inline-flex`; a button with stacked or left-aligned content sets its own `display` /
  `justify-content` in its class (e.g. `.rc-typecard`, `.ad-range__preset`).

- Evidence: `backend/pom.xml`
- Evidence: `frontend/src/app/AppShell.tsx`

## Primary runtime flows

### Report generation

```mermaid
sequenceDiagram
    participant B as Browser
    participant A as Spring API
    participant J as Report job (@Async)
    participant G as Google Sheets/Slides/Drive
    participant C as Anthropic Claude
    participant D as PostgreSQL
    B->>A: POST /api/v1/report-jobs (Bearer JWT)
    A->>D: Create report job
    A-->>B: Job id
    A->>J: Start async run
    J->>G: Read media plan / delivery sheets, build review sheet
    J->>C: Generate narrative copy (parallel calls on virtual threads)
    J->>G: Copy template deck, fill slides and charts
    J->>D: Record artifact, token usage, status
    B->>A: GET /api/v1/report-jobs/{jobId} (poll)
    A-->>B: Status and deck/sheet links
```

- Jobs can be resumed from an existing sheet (`/reports/{jobId}/resume`,
  `/reports/adopt-sheet`) or dismissed. Failures are caught on the async thread
  and recorded on the job; admins see them under `/admin/failures`.
- `UsageRollupRefresher` rebuilds the `usage_daily` rollup every 10 minutes
  (`@Scheduled`), which feeds the admin dashboard.
- `AbandonedJobReaper` fails, every 5 minutes, a queued/running job with no progress for
  `app.report-jobs.abandoned-after-minutes` (default 60) — its instance stopped mid-run.
- `ReportGenerationServiceImpl` only enqueues, opens the run's usage/failure scopes and picks a workflow:
  `DirectReportWorkflow` (from the request's grids) or `SlidesFromSheetWorkflow` (from a reviewed
  workbook), both in `service.reports.helpers` with their collaborators (`BreakdownSectionWorkflow`,
  `MpoDeckTokenWriter`, `ClaudeCallDispatcher`, `ReportTokenAssembler`, `ReportRunRecorder`).
- Evidence: `backend/service/src/main/java/com/aidigital/reportconstructor/service/reports/services/impl/ReportGenerationServiceImpl.java`
- Evidence: `backend/service/src/main/java/com/aidigital/reportconstructor/service/reports/helpers/SlidesFromSheetWorkflow.java`
- Evidence: `backend/service/src/main/java/com/aidigital/reportconstructor/service/admin/UsageRollupRefresher.java`

## API and security boundaries

- OpenAPI contract: `backend/application/src/main/resources/api/v1/specs/openapi.yaml`.
  Generated server sources live under `backend/application/target/generated-sources`
  and generated frontend types in `frontend/src/shared/api/generated/schema.d.ts`
  (`npm run generate:api`). Never edit either.
- Resource groups: `auth/me` (bootstrap), `sheets`, `google/status`,
  `line-items/match`, `placeholders`, `report-jobs`, `reports`, `admin/*`,
  `version`.
- Clerk with Google SSO authenticates users; the backend validates JWT issuer,
  signature, authorized party and the company email domain
  (`CompanyEmailDomainAuthorizationManager`, `ClerkJwtClaimsValidator`).
- Admin authorization: `AdminAccessPolicy` — an email is admin when it is on the
  configured allow-list (root admins) or has a UI-managed grant
  (`admin_users`). Comparisons are case-insensitive.
- Secrets stay in Replit Secrets, `backend/.env.local`, or the deployment
  secret manager; never in frontend code or public env vars.
- Evidence: `backend/application/src/main/java/com/aidigital/reportconstructor/security/SecurityConfig.java`
- Evidence: `backend/service/src/main/java/com/aidigital/reportconstructor/service/admin/AdminAccessPolicy.java`

## Data ownership and migrations

- PostgreSQL is the system of record for report jobs, admin grants, Claude
  token usage, the daily usage rollup, and MVP usage events. Generated decks
  and sheets are owned by Google Drive; the database stores their ids/links.
- Liquibase changelogs: `backend/migrations/src/main/resources/db/changelog`
  (master `db.changelog-master.xml`, change sets under `changes/`,
  `0001`–`0011`). Shared docs that say `backend/db` mean this path. Never rename
  the changelog directory or rewrite an applied changelog.
- Evidence: `backend/migrations/src/main/resources/db/changelog/db.changelog-master.xml`

## Caching and consistency

- No cache. The admin dashboard was the only cached read (an in-process `adminStats` snapshot); it was
  removed on 2026-11-13 (P6, D1) because the standard allows either the full `cache-management` mechanism
  or none, and the read does not need one. Measured on PostgreSQL 16 at 30,000 jobs and 300,000 Claude
  usage events, every read in one dashboard request takes about 0.11 s (median) for a 30-day window and
  about 0.20 s for a year.
- Every dashboard read is therefore correct on every instance, with no cross-node invalidation. Freshness is
  bounded by the `usage_daily` rollup alone: `app.usage-rollup.staleness-seconds` (120 s).
- Reintroduce caching only for a measured need, and only through the standard's `cache-management` module
  (Ehcache/JCache plus the database-outbox invalidation in `.claude/agent_docs/distributed_cache.md`).

## Multi-node operation

- **Scheduled rollup refresh** — every instance runs `UsageRollupRefresher` every 10 minutes. The rebuild is
  an idempotent upsert on `uq_usage_daily_grain` recomputed from `report_jobs`, so concurrent rebuilds on
  different instances converge (documented on `UsageDailyRepository#rebuildWindow`); the in-process
  single-flight only avoids duplicate work on one instance.
- **Report runs** execute on an `@Async` thread of the instance that accepted the request; their state is in
  `report_jobs`, so progress polling works from any instance. A run whose instance stops (deploy, restart,
  crash) is failed by `AbandonedJobReaper` (every 5 minutes, every instance) once it has gone
  `app.report-jobs.abandoned-after-minutes` (60) without progress — one conditional `UPDATE`, so concurrent
  sweeps claim each row once. A late finisher still ends `done`.
- **Claude concurrency** — `external.anthropic.max-concurrent-calls` is a per-instance semaphore; size it as
  the account budget divided by the replica count.
- No correctness depends on node-local state: the remaining in-memory structures are per-run scopes
  (usage, failure log) and per-instance HTTP client pools.

## External integrations

| Integration | Purpose | Protocol/authentication | Failure and retry policy |
|---|---|---|---|
| Clerk | User authentication; OAuth token access (`externalservices.clerk`) | OIDC/JWT; Clerk secret key | Fail closed on token validation; 15 s response timeout on Clerk API calls |
| PostgreSQL | Application persistence | JDBC | Transaction rollback; bounded Hikari pool |
| Google Sheets / Slides / Drive | Read source sheets, build review sheet, copy and fill deck templates, charts, sharing (`externalservices.google`) | Google SDK with a service account | Every call through `GoogleCallTimer`, directly or per attempt via `GoogleRequestRetrier`, tagged `google-sheets|slides|drive` + operation |
| Anthropic Claude | Narrative copy, digests, grounding/figure checks (`externalservices.anthropic`) | HTTPS Messages API; API key | Pooled client per per-call response timeout; token usage recorded per job |

- Spring HTTP clients come only from `PooledRestClientFactory`
  (`externalservices.common.http`): bounded Apache HttpClient 5 pool per client,
  explicit timeouts from `app.external.http.*`, metrics and Logbook interceptors.
- Evidence: `backend/external-services/src/main/java/com/aidigital/reportconstructor/externalservices/common/http/PooledRestClientFactory.java`
- Evidence: `backend/external-services/src/main/java/com/aidigital/reportconstructor/externalservices/google/GoogleCallTimer.java`

## Observability and operations

- Structured logs to stdout with a correlation id.
- Actuator health and Prometheus metrics; outbound calls on the
  `external.client.requests` timer.
- Logbook HTTP logging (inbound and outbound) is metadata-only by default
  (`app.logbook.include-bodies: false`); `Authorization`, `X-API-Key` and similar
  headers are masked.
- Admin dashboard (`/api/v1/admin/stats`, `/admin/failures`, `/admin/reports`)
  shows usage, token cost and failed jobs.
- Evidence: `backend/application/src/main/java/com/aidigital/reportconstructor/config/LogbookConfig.java`

## Build and test facts

- Run Maven from `backend/` (Checkstyle resolves its suppressions file relative
  to the working directory). Checkstyle (`backend/config/checkstyle.xml`):
  LineLength 120 (tab width 4), FileLength 2700. `ClaudeBatchPromptBuilder.java`
  sits at the FileLength limit — grow it only by moving code out.
- Production code never calls `now()`: inject `service.common.time.CurrentTime`
  (UTC). Static methods on beans/enums are replaced by small `@Component`
  resolvers (e.g. `NarrativeCopyLimitsResolver`).
- `mvn verify` enforces Maven dependency analysis in every module: declare the
  libraries a module compiles against, keep starters at `runtime` in
  `application`. Policy and allowlist: `backend/DEPENDENCY-ANALYSIS.md`.
- Backend tests: JUnit 5 + Mockito unit tests per module, MVC/controller tests
  in `application`. There is **no `*IT` integration-test layer**.
- Coverage gate (JaCoCo `check` in every module): a bare `mvn verify` is the
  strict 0.80 line / 0.70 branch gate. The committed `.template-phase` (`mvp`
  today) selects the phase through `scripts/lib/coverage-phase.sh`, which
  `scripts/local-verify.sh` and CI both use: `mvp` adds `-Pmvp`, which sets the
  parent floor of 0.30 / 0.25, plus each module's own `mvp` ratchet at its
  measured coverage, so coverage cannot slide. Today only `application` is
  below strict;
  `finalize-coverage` closes that before handoff. Never lower the parent
  defaults or pass `-Djacoco.*`; `check-coverage-integrity.sh` rejects both.
  The JaCoCo agent overwrites `jacoco.exec` (`append=false`), so a local run
  measures the same as CI's clean checkout.
- Frontend: Vitest + Testing Library (`npm test`), `npm run typecheck`,
  `npm run lint`, `npm run build`.
- Lint: ESLint 9 flat config `frontend/eslint.config.js` (typescript-eslint,
  local rule `project-rules/import-section-order` in `frontend/eslint-rules/`,
  `no-restricted-syntax` ban on inline `interface`/`type` and top-level `const`
  in `src/**/*.tsx`, tests included — mocks and fixtures live in
  `features/*/testing/*.ts`). Zero errors is the baseline; never disable rules.
- Pre-commit: Husky 9 (`.husky/pre-commit` runs `npm --prefix frontend run lint`),
  installed by the frontend `prepare` script (`scripts/prepare-husky.mjs`).
- Full local verification: `scripts/local-verify.sh` (backend `mvn verify` in the committed coverage phase,
  frontend `npm ci`, `generate:api`, `typecheck`, `lint`, `build`), followed by
  the blocking AIAE gate run `scripts/run-gates.sh`.

## Decisions, constraints, and known risks

| Decision or constraint | Reason | Consequence / follow-up |
|---|---|---|
| Contract-first OpenAPI | One backend/frontend API contract | Generated sources must not be edited |
| BrowserRouter + top-header shell | Elevate layout, direct links | SPA fallback must stay; do not introduce a sidebar |
| `CLAUDE.md` and `.claude/{rules,agent_docs,skills}` are AIAE-managed | Template upgrades refuse to overwrite locally edited fixtures | Project facts live in this file; upgrade by re-running the standard's `install-claude-fixtures.sh` |
| AIAE gates are blocking (`scripts/run-gates.sh`, CI `static-checks`) | Keep the converged rules from regressing | The only exceptions are the carried decisions in `scripts/gates-carried.sh` (D2 `.replit` port 5173, D3 Vitest 4); `verify-gates.sh` still runs every other check, on a copy with just those neutralised, and a carry that is fixed fails until removed |
| Liquibase `preConditions` on every changeSet | Shared rule | `0001-usage-events.xml` and `0002-report-jobs.xml` predate it; every **new** changeSet declares them; never rewrite applied changelogs |
| BigQuery query rules (`13-bigquery.md`) | Shared rule | No BigQuery code; delivery data arrives through exported Google Sheets (Elevate). Applies only if BigQuery access is added |
| GSD workflow (`skill-selection.md`) | Shared rule | GSD is not installed; use `task-workflow` and the focused skills |
| Shared rules this project does not meet yet | Code predates the contract | Apply the rule to **new** code; do not retrofit existing code as a side effect of unrelated work — raise it as its own task |
