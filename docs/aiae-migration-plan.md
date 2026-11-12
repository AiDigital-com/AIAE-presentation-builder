# AIAE Convergence — Migration Plan

Brings this repository onto the AIAE generated-project standard
(`AIAE-replit-llm-aux`), following the phased approach the AIAE onboarding
platform used (its PR #3, `PDI_080`). This file is the single source of truth
for phase scope and status; [`aiae-migration-log.md`](./aiae-migration-log.md)
records what was actually measured.

## Ground rules

- **Do not edit managed fixtures.** `CLAUDE.md`, `AI-DEVELOPMENT-GUIDE.md`,
  `GDS-WORKFLOW-README.md`, `agent-payload.skills`, `.claude/agent_docs/**`,
  `.claude/rules/**`, `.claude/skills/**` and `.claude/tasks/README.md` are
  checksummed in `.claude/.aiae-fixtures-manifest`. Any local edit makes the next
  `install-claude-fixtures.sh` run abort. Project facts and project-specific
  rules go in [`architecture-overview.md`](./architecture-overview.md).
- **Do not normalise line endings on those paths.** `.gitattributes` marks them
  `-text` because the standard itself is mixed LF/CRLF.
- **Never rename `backend/migrations/src/main/resources/db/changelog/`** and never
  rewrite an applied changeSet (Liquibase records the classpath path and checksums).
- **Gates are report-only until P15.** `scripts/gates-report.sh` always exits 0;
  the CI `static-checks` job is `continue-on-error`. Do not turn a gate green by
  renaming or by weakening a checker; fix the code or record a decision below.
- **One branch per phase or slice**, each reviewed and verified on its own.
- **Python 3.9+ must be on `PATH` as `python3`.** On Windows the Store stub prints
  `Python` and exits 0, which makes every Python gate look green.

## Upgrading the standard

1. Export the standard with LF bytes:
   `git -C ../AIAE-replit-llm-aux -c core.autocrlf=false archive HEAD | tar -x -C <tmp>`.
2. `bash <tmp>/scripts/install-claude-fixtures.sh <this repo>` (needs `rsync`; on
   Windows a `cp -a` shim works). It refuses to overwrite locally edited fixtures.
3. Mirror `.agents/skills/<skill>` for every name in `agent-payload.skills`, then
   run `python3 scripts/lib/rewrite-installed-documentation-paths.py .agents/skills`.
4. Update `.template-version` and `llm-aux.lock`, review the diff, commit.

## Phases

| Phase | Scope | Status |
|---|---|---|
| **P0** Baseline and decisions | This plan, the log, the gate baseline | Done 2026-11-12 (decisions D1–D5 open) |
| **P1** Agent surface | Managed `.claude/`, `CLAUDE.md`, `.agents/skills`, `AGENTS.md`, `replit.md`, `llm-aux.lock`, `.template-*`, `.mcp.json`, `.gitattributes` | Done (`7f14b3e`) |
| **P2** Harness and local runner | `verify-gates.sh`, `structure-lint.sh`, `scripts/lib` checkers, `gates-report.sh`, `local-verify.sh` runs the gates, CI `static-checks` job, `checkstyle-test-fields.xml` (blocking in `mvn verify` since all 87 test fields were made `private`) | Done 2026-11-12 |
| **P3** Docs and Replit contract | `architecture-overview.md`, `README.md`, inline `.replit` workflows, `replit-run.sh` sources `replit-env.sh`, Context7 | Done 2026-11-12 |
| **P3b** Maven dependency analysis | Directly used libraries declared in all 7 module poms, runtime-only starters/modules at `runtime` scope, `maven-dependency-plugin analyze-only` (`failOnWarning`) enforced in `mvn verify`, `backend/DEPENDENCY-ANALYSIS.md` | Done 2026-11-12 — blocking in the Maven build; fat jar not yet booted against a real DB |
| **P4** `backend/observability` | Outbound metrics module | Already in place |
| **P5** `backend/migrations` | Liquibase module with preconditions | In place; `0001`/`0002` predate preconditions and stay (never rewrite) — see D4 |
| **P6** Distributed cache | `backend/cache-management` | Open decision D1 |
| **P7** Multi-node work | `UsageRollupRefresher` (`@Scheduled`), `@Async` report jobs, `adminStats` cache — make idempotent/coordinated per `14-performance.md` | Done 2026-11-12 — rollup rebuild already an idempotent upsert (verified, no change); dashboard cache keyed by rollup version (cross-node freshness ≤ staleness budget); `AbandonedJobReaper` fails runs orphaned by a stopped instance; Claude concurrency documented as per-instance |
| **P8** Boundary splits | Entity-service and helper extraction per `structure/*`; module direction `service → external-services` with `external-services` a leaf | Done 2026-11-12 — every provider converged (Clerk, Drive, Sheets, Slides, workbook, charts + pacing pivots, Anthropic), Claude prompt logic and the port adapters (`service.integration`) in `service`, module edge flipped; structure-lint passes (D6 resolved) |
| **P9** Coverage phase | `mvp` coverage profile driven by `.template-phase` | Done 2026-11-12 — bare `mvn verify` strict 0.80/0.70; `-Pmvp` (parent floor 0.30/0.25 + per-module ratchets at measured coverage) selected by `scripts/lib/coverage-phase.sh` in CI and `local-verify.sh`; old per-module default overrides and the no-op `handoff` profile removed |
| **P10** API validation | OpenAPI: closed schemas, descriptions, `$ref` enums, input constraints; one 400 MVC test per constrained operation | Done 2026-11-12 |
| **P11** Thin controllers and service shape | Controller logic → services/mappers; split `ReportGenerationServiceImpl`, `LineItemMatcherServiceImpl`, `AdminStatsServiceImpl`; `CurrentTime`; MapStruct for entity mapping; remove static factories | Done 2026-11-12 — `CurrentTime`, static factories, MapStruct entity mapping, thin controllers, mapper package layout and service splits (`ReportGenerationServiceImpl` 1,723 → 138 lines) |
| **P12** Frontend tooling | ESLint, import order, Husky; remaining `check-frontend-ui-rules` findings | Done 2026-11-12 (tooling earlier; UI-rule findings fixed, visual check pending) |
| **P15** Make it stick | Flip `gates-report.sh`, CI `static-checks` and the test-field Checkstyle profile to blocking | Not started |

## Open decisions (product/tech owner)

| ID | Question | Recommendation |
|---|---|---|
| D1 | Adopt `backend/cache-management` (distributed cache with DB-outbox invalidation)? | **Decline** for now: the only cache is one admin-dashboard snapshot; record as a deliberate divergence. Revisit if more caches are added or PROD runs >1 replica with stricter freshness needs. |
| D2 | Stop exposing Vite's port 5173 in `.replit` (gate: "Vite must not run on/expose 5000")? | Keep until someone verifies the Replit workspace preview still works without it; then remove. |
| D3 | Pin Vitest to the firewall-approved `^3.2.6` (we use `^4.1.11`)? | Ask whether the Replit firewall allow-list still requires 3.x; otherwise request the standard to accept 4.x. |
| D4 | Liquibase `0001`/`0002` have no `preConditions` | Accept permanently (applied changelogs are immutable); request an allow-list in `check-liquibase-preconditions.py` upstream. |
| D5 | Lifecycle phase `mvp` vs `engineering` (`.template-phase`) | Stay `mvp` while usage telemetry is on; switch via `finalize-coverage` before handoff. |
| D6 | Module direction: ports in `service`, adapters in `external-services` (standard: leaf `external-services`, `service → external-services`, no `application → external-services`) | **Resolved 2026-11-12** — converged to the standard: `external-services` is a leaf of provider clients, `service` depends on it and implements its ports in `service.integration`, `application` no longer depends on `external-services`. |

## Carried gate failures (accepted until their phase)

- `verify-gates.sh`: `.replit` exposes 5173 (D2); Vitest pin (D3); Logbook literal
  `.strategy(new WithoutBodyStrategy())` — our `LogbookConfig` builds
  `WithoutBodyStrategy` by default via `strategy(props)`, so this is a literal-match
  false negative (upstream change request).
- `check-liquibase-preconditions`: `0001`, `0002` (D4).
- Everything else in the baseline is real backlog owned by P7–P12.
