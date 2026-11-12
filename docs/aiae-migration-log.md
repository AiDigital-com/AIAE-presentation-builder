# AIAE Convergence — Migration Log

Measured facts only, newest first. Plan and scope: [`aiae-migration-plan.md`](./aiae-migration-plan.md).

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
