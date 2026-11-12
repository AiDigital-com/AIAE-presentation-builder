# AIAE Convergence — Migration Log

Measured facts only, newest first. Plan and scope: [`aiae-migration-plan.md`](./aiae-migration-plan.md).

## 2026-11-12 — Mapper package layout (P11 slice, branch `PDI_240`)

- Application API mappers moved to `mappers/<aggregate>/` (`admin`, `auth`, `reports`, `version`);
  service entity mappers to `service/mappers/<aggregate>/` (`admin`, `reports`). `git mv`, so
  history follows.
- The same structure-lint rules forbid `new *V1(...)` inside mappers, so the four hand-built
  envelopes became MapStruct mappings over typed service results:
  - `ReportHistoryService.historyForOwner` returns `ReportHistory(total, reports)`;
    `AdminManagementService.list/add/removeAdmin` return `AdminList(admins)`.
  - `DataSourceApiMapper.tabNotFound` / `toSummary` are MapStruct methods (same JSON: empty
    lists default, `ok=false`, `error="tab_not_found"`, `rows`/`cols` 0).
  - The hand-written `LineItemMatchResultMapper` class is gone: its indexing / numbering logic is
    now `service` `LineItemMatchViewAssembler` (+ `LineItemMatchViewService`), producing
    `LineItemMatchView`/`TacticMapping`; `LineItemMatchApiMapper` is a pure MapStruct interface.
    Generated code keeps insertion-ordered maps (`LinkedHashMap`).
- `structure-lint.sh` now passes every mapper rule and stops at
  **"application/pom.xml must not depend on external-services — route through service/"**. The
  standard has `service → external-services`; this project has the opposite (ports in `service`,
  adapters in `external-services`). Fixing it means re-homing the outbound ports — P8 boundary work,
  not done here.
- Gates unchanged at **10 of 21 failing** (structure-lint still fail-fast, one rule further).
- `mvn verify`: BUILD SUCCESS, 1,187 tests (+2 `LineItemMatchViewAssemblerTest`), 0 failures.

## 2026-11-12 — Thin controllers (P11 slice, branch `PDI_240`)

- `DataSourceController.readSheet`: the try/catch + tab-not-found detection moved to the new
  `SheetTabReadService` (returns `SheetTabRead`: data, or the visible tabs); the mapper's
  `toReadResult` builds the response. **Carried quirk, unchanged on purpose:** every `C001`
  message reads "Resource not found: …", so any `C001` from the sheet read (e.g. a missing
  spreadsheet) is answered as `tab_not_found` with no tabs — now pinned by
  `SheetTabReadServiceImplTest`. Worth a product decision.
- `ReportsController` / `AdminController`: `*V1` envelopes are built in mappers
  (`ReportsApiMapper.toReportList`, `AdminManagementApiMapper.toList`, new
  `AdminReportsApiMapper.toReportPage` using `ReportsApiMapper` for rows).
- `service` → `event-logging-to-db-feature` restored (structure-lint requires it while the
  module exists) and allowlisted in `DEPENDENCY-ANALYSIS.md`; P3b had removed it as unused.
- `check-thin-controllers`: FAIL → PASS. **10 of 21 failing.** `structure-lint.sh` now gets past
  controllers and stops at the mapper layout rule (`mappers/<aggregate>/` instead of
  `<aggregate>/mappers/`) — a package move across `application`, not done here.
- `mvn verify`: BUILD SUCCESS, 1,185 tests, 0 failures (tab-picker tests moved from
  `application` to `service`, +4 new).

## 2026-11-12 — Manual entity mapping → MapStruct (P11 slice, branch `PDI_240`)

- New service-layer MapStruct mappers on `ServiceMapperConfig` (unmapped target = ERROR),
  each `@BeanMapping(ignoreByDefault = true)` with every field mapped explicitly:
  `AdminUserEntityMapper.toEntity`, `ReportJobEntityMapper.toQueuedJob` (queued / step 0 of 7 /
  "Queued…" as named constants), `ClaudeUsageEventEntityMapper.toEntity` (null scope → null
  job/owner, as before). Defaulting logic (email normalisation, `"unknown"` label) stays in
  the services.
- `check-production-manual-mapping`: FAIL → PASS. **11 of 21 failing.**
- `mvn verify`: BUILD SUCCESS, 1,180 tests (+4 mapper tests), 0 failures.

## 2026-11-12 — Static factories and direct `now()` (P11 slice, branch `PDI_240`)

- New `service.common.time.CurrentTime` / `CurrentTimeImpl` (standard interface plus
  `nowOffsetDateTime()`, UTC). All 22 direct `now()` calls replaced in 9 classes
  (`ReportJobProgressHelperImpl` ×12, `AdminStatsServiceImpl`, `UsageRollupRefresher`,
  `AdminUserServiceImpl`, `ClaudeUsageTrackerImpl`, `UsageDailyServiceImpl`,
  `ReportFileNamerImpl`, `SheetRowHelperImpl`, `GlobalExceptionHandler`).
  Behaviour: unchanged in production (JVM runs `-Duser.timezone=UTC`); on a developer
  machine in another zone, "today"/file-name stamps now follow UTC instead of local time.
- 3 static factories replaced by beans: `NarrativeCopyLimitsResolver`,
  `PaidCopySlotResolver` (service) and `PaidChartStyleResolver` (external-services);
  `PaidCopySlot` gained an instance `inReport(eom)`.
  `ReportGenerationServiceImpl` now has 40 injected collaborators (+1); it is split in P11.
- Gates: `check-production-static-methods` and `check-production-current-time` FAIL → PASS.
  **12 of 21 failing.**
- `mvn verify`: BUILD SUCCESS, 1,176 tests (+4: `CurrentTimeImplTest` ×2,
  `PaidCopySlotResolverTest`, `PaidChartStyleResolverTest`), 0 failures.

## 2026-11-12 — Test field visibility made blocking (branch `PDI_240`)

- 85 package-private test fields made `private` (84 in 16 `backend/service` test
  classes, 1 in `application`'s `LiquibaseChangelogSmokeTest`); together with the
  2 fixed earlier in `event-logging-to-db-feature`, all 87 findings are cleared.
  Declaration change only — no test logic touched.
- `checkstyle-test-field-visibility` no longer sets `failOnViolation=false`, so it
  now fails `mvn verify` like the main Checkstyle profile.
- `mvn verify`: BUILD SUCCESS, 1,172 tests, 0 failures, 0 Checkstyle violations.

## 2026-11-12 — P3b Maven dependency analysis (branch `PDI_240`)

- `maven-dependency-plugin:3.7.1:analyze-only` bound to `verify` in every module,
  `failOnWarning=true`, `ignoreNonCompile=true`, allowlist = `lombok` only.
  `check-maven-dependency-analysis`: FAIL → **PASS**. Gates now 14 of 21 failing.
- All 7 module poms declare the libraries they compile against instead of starters;
  `application` keeps every starter at `runtime`, plus `external-services`,
  `observability`, `migrations` at `runtime`. `service` no longer depends on
  `event-logging-to-db-feature` (no bytecode reference); `external-services` does.
  `commons-csv` removed (no usage anywhere).
- `mvn verify`: BUILD SUCCESS, 1,172 tests, 0 failures. One test-classpath fix:
  `external-services` gets `spring-boot-starter-validation` at `test` scope
  (`PooledHttpClientPropertiesTest` bootstraps a Bean Validation provider).
- **Fat-jar comparison** (`BOOT-INF/lib`, `HEAD` vs this change): 191 → 190 jars.
  Removed `commons-csv-1.12.0`; `commons-io` 2.17.0 → 2.16.1 (2.17.0 only came in
  through `commons-csv`; 2.16.1 is what Liquibase declares). Two regressions found
  by the comparison and fixed before landing: `json-smart` (+`accessors-smart`,
  `asm`) — needed at runtime by `json-path` via `logbook-json`, previously present
  only through test-scope mediation — now declared `runtime`; servlet API declared
  as `tomcat-embed-core` with the starter's `tomcat-annotations-api` exclusion, so
  no duplicate servlet jars.
- `ApplicationSmokeTest` boots the context on the test classpath. **The packaged
  jar was not started against PostgreSQL/Clerk**; do that on DEV or locally before
  release.

## 2026-11-12 — P0 / P2 / P3 (branch `PDI_240`)

Standard: `AIAE-replit-llm-aux` `a69cd5862ea109f577b72ed08b79e1869eea545a`
(llm-aux `74d9683`, v0.3.2). Python 3.12.10. JDK 21, Maven (IntelliJ bundled).

### Gate baseline — `bash scripts/gates-report.sh`

15 of 21 gates failing (report-only).

| Gate | Result | Count / first finding |
|---|---|---|
| structure-lint.sh | FAIL | fail-fast: controllers construct generated `*V1` DTOs |
| verify-gates.sh | FAIL | own assertions: `.replit` 5173 (D2), Vitest pin (D3), Logbook literal |
| check-agent-surfaces | PASS | `active` |
| check-architecture-overview | PASS | `mvp` |
| check-installed-documentation-links | PASS | |
| check-api-client-paths | PASS | |
| check-openapi-strict-schemas | FAIL | top-level object schemas without `additionalProperties: false` |
| check-openapi-documentation | FAIL | 130 |
| check-openapi-enums | FAIL | 4 inline enums |
| check-openapi-input-constraints | FAIL | 32 |
| check-api-validation-tests | FAIL | 14 constrained operations, 0 `isBadRequest()` tests |
| check-maven-dependency-analysis | FAIL | policy missing (P3b) |
| check-liquibase-preconditions | FAIL | 3 changeSets in `0001`, `0002` (D4) |
| check-thin-controllers | FAIL | 7 (`AdminController`, `DataSourceController`, `ReportsController`) |
| check-frontend-ui-rules | FAIL | 77 |
| check-production-static-methods | FAIL | 3 |
| check-production-magic-values | PASS | |
| check-production-current-time | FAIL | 22 |
| check-production-manual-mapping | FAIL | 3 |
| check-service-contract-quality | FAIL | 5 (`ReportGenerationServiceImpl` 1,722 lines / 39 deps) |
| check-coverage-integrity | PASS | |

### Checkstyle test-field visibility (P2, report-only)

`mvn -DskipTests verify` with `checkstyle-test-fields.xml`: 84 violations, all in
`backend/service` tests (package-private test fields). 2 in
`event-logging-to-db-feature` were fixed (`UsageLoggingAspectUnitTest`). Build green.

### Maven dependency analysis probe (P3b input)

`dependency:analyze-only` over all modules: every module declares Spring Boot
starters but compiles against their transitive libraries (used-undeclared), and
`application` declares ~15 runtime-only starters/modules as compile scope
(unused-declared). Wiring `failOnWarning=true` fails the build at `domain`, so
the plugin and `DEPENDENCY-ANALYSIS.md` are deferred to P3b.

### Replit contract (P3) — not verified on Replit

`.replit` workflows now inline the dev commands (`source scripts/replit-env.sh`,
`npm run generate:api`, `mvn -f backend/application/pom.xml`), and
`scripts/replit-run.sh` sources `scripts/replit-env.sh` after `deploy-env.sh`.
Behaviour is meant to be identical to `replit-dev-*.sh`, but **neither the
workspace Run button nor a Replit deploy was exercised**; whoever runs them next
should confirm both boot.

## 2026-11-12 — P1 agent surface (`7f14b3e`)

Installed 82 managed fixtures via `install-claude-fixtures.sh` from an LF export;
manifest 82/82. `.agents/skills` mirrors 15 payload skills. Four source comments
repointed from `templates/generated-project/` to `.claude/agent_docs/`.
`project_facts.md` moved into `docs/architecture-overview.md`.
