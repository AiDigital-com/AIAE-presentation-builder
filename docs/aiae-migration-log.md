# AIAE Convergence — Migration Log

Measured facts only, newest first. Plan and scope: [`aiae-migration-plan.md`](./aiae-migration-plan.md).

## 2026-11-13 — `service` reaches the strict coverage gate (D5 groundwork, branch `PDI_240`)

- **The P9 baseline was wrong for `external-services`.** JaCoCo's agent appends to `target/jacoco.exec` by
  default. The local exec files still held hits from earlier builds, including tests that P8 moved out of
  `external-services`, so the P9 measurement read 0.6338 line coverage. A clean measurement gives 0.6290
  (2,848 of 4,528), below the 0.63 floor, so a clean CI build has failed since `24cd039`.
  - Fixed: the `external-services` `mvp` floor is now 0.62 line (branch stays 0.54, measured 0.5420).
  - Fixed: the parent pom sets `append=false` on `prepare-agent`, so local runs measure like CI.
  - The other modules' clean numbers match P9 exactly.
- **`service` branch coverage.** 66.49 % → 74.09 % (4,576 → 5,099 of 6,882), and lines 82.4 % → 87.4 %. That
  is 523 more branches covered, with 1,032 tests (was 854). New and extended tests:

  | Group | Classes | Missed branches |
  |---|---|---|
  | Admin | `AdminManagementServiceImpl`, `AdminAccessPolicy`, `AdminRollupTokenTotals`, `AdminRollupUserStats`, `AdminActiveUsersBuilder` | 90 → 0 |
  | Job helpers | `ReportJobProgressHelperImpl`, `ReportSummaryAssemblerImpl`, `ClaudeCallDispatcher` | 44 → 0 |
  | Engine | `ChartPivot`, `CampaignPacingResolvers`, `ChannelSlideResolvers`, `CampaignFlightResolver` | 181 → 0 |
  | Report helpers | `ReportTokenAssembler`, `ClaudeResponseNormalizer`, `ReportGenerationChartHelperImpl`, `SheetRowHelperImpl` | 206 → 4 |

  - The 4 uncovered branches are unreachable defensive checks: `String.split` never yields a null element, and
    one `jobs.isEmpty()` guard is dead.
  - New tests use no `any()` and build their fixtures inside each test. Instancio is not a project dependency,
    so records are built with their constructors.
- **`service` floor.** The module's `mvp` floor is now the strict 0.80 / 0.70, so it cannot slide back. Still
  below strict: `external-services` (0.629 / 0.542) and `application` (0.467 / 0.396).
- **Production observations from writing the tests, not changed:**
  - `AdminManagementServiceImpl.currentAdmins` would throw an NPE on an `admin_users` row with a null email.
  - `AdminRollupUserStats.build` sorts by report count, but its JavaDoc says "biggest spenders first".
  - `CampaignPacingResolvers`: three resolvers lack the `data == null` guard that `resolveTotalImpsPace` has;
    a short-circuit currently prevents an NPE.
  - `ClaudeResponseNormalizer.limitGeoSummary` returns null for a reply that starts with a comma.
  - `ReportGenerationChartHelperImpl.buildBreakdownCharts` has a dead `jobs.isEmpty()` guard.

## 2026-11-13 — Distributed cache (P6 / D1, branch `PDI_240`)

- **Starting point.** `.claude/agent_docs/distributed_cache.md` allows either the full `cache-management`
  mechanism or no cache, and calls partial states invalid. We had one in-process Spring cache, `adminStats`,
  and no outbox:
  - its P7 timestamp version broke rule 14's ban on timestamp polling;
  - clearing failures evicted only the local instance's cache.
- **Measurement.** A throwaway Testcontainers probe (deleted afterwards) on PostgreSQL 16, seeded with 30,000
  `report_jobs` (300 users, two years) and 300,000 `claude_usage_events`. All eleven reads of one dashboard
  request, uncached:

  | Window | Median | p90 | First read |
  |---|---|---|---|
  | 30 days | 108 ms | 141 ms | 393 ms |
  | 365 days | 204 ms | 234 ms | 213 ms |

  Slowest single reads:
  - 30 days: `activeDays` 24 ms, `listOwners` 12 ms.
  - 365 days: `aggregateByLabel` 74 ms, `activeDays` 39 ms.

  For comparison, a full rollup rebuild takes 645 ms, and a stale request already pays for a rebuild without
  any cache.
- **Decision.** The cache is removed and the module is not adopted:
  - deleted `CacheConfig` (`@EnableCaching`), `AdminStatsCache`, `AdminStatsKey`;
  - `AdminStatsServiceImpl` refreshes the rollup, then assembles;
  - `AdminFailuresServiceImpl` no longer evicts;
  - `UsageRollupRefresher.ensureFresh()` returns `void` and `lastRefreshedAt()` is package-private again.

  The dashboard is now correct on every instance, and freshness depends only on the rollup's 120 s staleness
  budget.
- Tests: `AdminStatsServiceImplTest` rewritten (3 tests: non-admin, refresh then assemble, refresh failure);
  `AdminFailuresServiceImplTest` no longer verifies eviction.

## 2026-11-12 — Gates made blocking (P15, branch `PDI_240`)

- **Hidden checks.** `verify-gates.sh` stops at its first failure, and it had failed at the `.replit` check
  (line ~61) since P2, so none of its later checks had been seen. I ran it on a copy with the four known items
  patched; it passed. There were no hidden failures.
- **D4 fixed.**
  - Preconditions added to `0001` (`not tableExists usage_events`), `0002` changeSet 1
    (`not tableExists report_types`, `report_jobs`) and `0002` seed (`sqlCheck` count of EOC/EOM = 0).
    All use `MARK_RAN`, the style of `0003`+.
  - Checked with a throwaway Testcontainers test, PostgreSQL 16, Liquibase 4.29.2:
    - migrate with the HEAD files;
    - `validate()` with the new files, no checksum error;
    - `listUnrunChangeSets` is empty;
    - `update` is a no-op.
  - Checksums unchanged (v9).
- **Logbook literal fixed.** `LogbookConfig` builds with `.strategy(new WithoutBodyStrategy())`, and
  `bodyLoggingOverride(props)` returns `DefaultStrategy` only when `app.logbook.include-bodies` is set.
  Behaviour is unchanged. `LogbookConfigTest` was updated.
- **D2 and D3 carried, not fixed.**
  - D2: the standard has no `[[ports]]` entry for 5173, but our Clerk dev pattern `https://*.replit.dev:5173`
    (`270cb74`) relies on that URL. Removing the entry needs a check on Replit.
  - D3: Vitest 4 was a deliberate upgrade (`7e7d33a`).
- **The runner.** `scripts/run-gates.sh` replaces `gates-report.sh` and exits 1 on any failing gate. It runs from
  `local-verify.sh` and from the CI `static-checks` job, which is no longer `continue-on-error`.
  - Carried items live in `scripts/gates-carried.sh`, each with a `present` check and a `neutralise` rewrite.
  - `verify-gates.sh` runs through its supported `VERIFY_ROOT` on a copy of the working tree with only those
    items neutralised.
  - Negative checks on throwaway copies:
    - dropping `@Async("usageLoggingExecutor")` (a check after both carried items) fails the run;
    - pinning Vitest back to `^3.2.6` fails with "stale carry D3";
    - the real `.replit` and `package.json` are never touched.
- **Test-field Checkstyle** has been blocking in `mvn verify` since P2; no change.
- Result: `bash scripts/run-gates.sh` reports all 21 gates passing (carried: D2, D3), exit 0.

## 2026-11-12 — Coverage phase (P9, branch `PDI_240`)

- Before: `service`, `external-services` and `application` overrode the parent's 0.80/0.70 in their
  default `<properties>`, so even a bare `mvn verify` was relaxed. `event-logging-to-db-feature` lowered
  branch coverage to 0.60. A `handoff` profile restated the defaults and changed nothing.
  `check-coverage-integrity` therefore had no phase mechanism to verify.
- After:
  - The parent keeps strict defaults (0.80 line / 0.70 branch), and no module overrides them.
  - The parent's `mvp` profile is the floor, 0.30 / 0.25.
  - Each module has its own `mvp` profile ratcheted to its measured coverage, or to strict where the
    module already meets it.
  - CI and `scripts/local-verify.sh` choose the phase only through `scripts/lib/coverage-phase.sh`
    from `.template-phase` (`mvp`).
  - `local-verify.sh` now runs `verify` (it was `test`), so it enforces the gate locally too.
- Measured coverage (JaCoCo CSV) and the `mvp` ratchet per module:

  | Module | Line | Branch | `mvp` ratchet (line / branch) |
  |---|---|---|---|
  | `domain` | 0.8235 | 0.9167 | 0.80 / 0.70 (strict) |
  | `observability` | 1.0 | 1.0 | 0.80 / 0.70 (strict) |
  | `event-logging-to-db-feature` | 0.9013 | 0.7823 | 0.80 / 0.70 (strict) |
  | `service` | 0.8236 | 0.6650 | 0.80 / 0.66 |
  | `external-services` | 0.6338 | 0.5424 | 0.63 / 0.54 |
  | `application` | 0.4667 | 0.3958 | 0.46 / 0.39 |
- Verification:
  - `mvn -o -Pmvp verify` passes.
  - A bare `mvn -o verify` fails at `external-services`.
  - Probing all modules with the halt switched off (local diagnosis only) reports violations in exactly
    three modules: `external-services` (0.63 / 0.54), `service` (branch 0.66) and `application`
    (0.46 / 0.41 by the check's bundle count). `domain`, `observability` and
    `event-logging-to-db-feature` meet strict.
  - `check-coverage-integrity`: `OK (phase=mvp, strict 0.80/0.70, mvp 0.30/0.25)`.
  - Gates: still 2 of 21 failing (`verify-gates.sh` for D2/D3, and `check-liquibase-preconditions`).

## 2026-11-12 — Multi-node work (P7, branch `PDI_240`)

- Inventory of node-local state and work: one `@Scheduled` job (`UsageRollupRefresher`), `@Async` report runs,
  the `adminStats` cache, the Claude transport's semaphore, per-run `ThreadLocal` scopes (usage, failure
  log), per-instance HTTP client pools.
- **Rollup refresh — no change.** The rebuild is delete-window + `INSERT … ON CONFLICT ON CONSTRAINT
  uq_usage_daily_grain DO UPDATE`, every row recomputed from `report_jobs`; concurrent rebuilds on different
  instances converge (the repository JavaDoc and changelog `0009` state this intent). Each instance's timer
  repeats a cheap grouped scan — duplicated work, not a correctness problem.
- **Dashboard cache — fixed.** Eviction was per instance: after instance A rebuilt, B saw a fresh rollup,
  skipped its own rebuild and kept serving its older snapshot until its next 10-minute tick. The key is now
  `AdminStatsKey(range, rollupVersion)` with the version = `usage_daily` `max(refreshed_at)`
  (`UsageRollupRefresher#lastRefreshedAt`, now public): a rebuild anywhere moves it, so every instance's dashboard
  is at most the staleness budget (120 s) old. One extra `max()` read per dashboard request. Residual: a rebuild
  that writes no row cannot move the version; that case stays bounded by each instance's timer.
- **Abandoned report runs — new.** A run whose instance stopped stayed `queued`/`running` for ever (the
  progress view polled indefinitely; the admin in-flight counter stayed high) — on a single instance too, at
  every deploy. New `service.reports.jobs.AbandonedJobReaper` (every 5 min, every instance) calls
  `ReportJobProgressHelper.failAbandonedJobs` → `ReportJobRepository.failInFlightNotUpdatedSince`, one
  conditional `UPDATE` (status `error`, a "server restarted, please run it again" message) for jobs without
  progress for `app.report-jobs.abandoned-after-minutes` (default 60; a live run writes progress at each of
  its ~7 steps). Concurrent sweeps claim each row once; a run that was only slow still ends `done`.
- **Claude concurrency** — `max-concurrent-calls` is per instance; `application.yml` now says to size it as
  the account budget divided by replicas. Coordinating it across instances would need shared infrastructure
  the rules say not to add without measured need.
- Tests: `AbandonedJobReaperTest` 3, `ReportJobProgressHelperImplTest` +1 (cutoff and statuses),
  `AdminStatsServiceImplTest` (versioned key), new `ReportJobAbandonedSweepTest` — the bulk update against real
  Postgres 16 (Testcontainers) with the Liquibase schema. `service` 850 → 854, `application` 93 → 94.
  `mvn -Pmvp verify`: BUILD SUCCESS. Gates: 2 of 21 failing (unchanged; D2/D3, D4).

## 2026-11-12 — Module edge flipped: `service → external-services` (P8, D6 resolved, branch `PDI_240`)

- The 12 port adapters and their 12 tests moved from `externalservices.bridge` to `service.integration`
  (`git mv`). They are the only service classes that touch provider types.
- Poms: `service` declares `external-services`; `external-services` no longer declares `service` or
  `event-logging-to-db-feature` (its only user, `SheetQueryServiceImpl`, moved) and keeps `spring-beans` at
  `test` scope (its main-code use left with the adapters); `application` no longer declares
  `external-services` — it reaches the fat jar through `service`. `service` additionally declares `spring-web`
  (`HttpStatus` in `SheetsFailureTranslator`) and `spring-boot-test` (test). Reactor order is now
  `external-services` before `service`.
- Tests that crossed the new boundary: `ClerkConfigTest` split — the client half stays in
  `external-services`, the adapter wiring half is `service.integration.ClerkUserGoogleTokenProviderWiringTest`;
  the creative-token contract test left `RealSlidesProviderTest` for
  `service.reports.helpers.impl.CreativeBreakdownSlideTokenContractTest`, renumbering through the new public
  `BreakdownSlideNaming.renumberToken` (the `n`-variable rule moved there; `RealSlidesProvider.renumber`
  delegates, unchanged behaviour). `BreakdownChartCatalog`'s JavaDoc link now names `DeckBreakdown`.
- `external-services` main and test sources: **no reference to `service` or `domain`**.
- `mvn -Pmvp verify`: BUILD SUCCESS, every module's coverage floor met. Tests: `service` 801 → 850,
  `external-services` 303 → 256, `application` 93 (+2 overall: the new adapter wiring test).
- **Gates: 2 of 21 failing** — `structure-lint.sh` now **passes**; remaining: `verify-gates.sh` (D2 `.replit` port,
  D3 Vitest pin, Logbook literal) and `check-liquibase-preconditions` (D4).

## 2026-11-12 — Claude prompt and reply logic moved into the service (P8 / D6, step A2, branch `PDI_240`)

- `git mv`'d from `externalservices.anthropic` to `service.reports.claude`: 19 classes
  (`RealClaudeClient`, `StubClaudeClient`, `ClaudeBatchPromptBuilder`, `EomPromptBuilder`,
  `ClaudeResponseNormalizer`, `AnthropicClaudeClientFlavors`, `EomClaudeClientConfig`, `ClaudeCompression*`,
  `ClaudeSectionAttempt`, `LeadPhraseSpec`, `MpoPromptSpec`, `PaidPromptSpec`, `PromptTokenEstimator`,
  `RealLineItemMatchAssistant`, `StubLineItemMatchAssistant`, `TacticThoughtsCompleteness`, `WorkbookGeoFilter`)
  and their 9 test classes. No name collided with existing service types; bean names and conditions
  (`external.anthropic.api-key`) are unchanged.
- New service port `ports.ClaudeTransport` (+ `ports.ClaudeReply`): `callJsonObject`, `callJsonArray` as before;
  `callText` replaces the five `callRaw` + reply-text pairs and keeps their distinction exactly (`null` = no
  reply, so the digest "failed" warnings still fire only then; a reply's `text` may be `null`).
  `CACHE_BREAKPOINT` is declared on the port; `ClaudeTransportBridgeTest` pins it equal to the transport's.
- `bridge.ClaudeTransportBridge` implements the port over `AnthropicClient`, conditional on the API key like the
  transport. `AnthropicCallListener.estimatedUsage(label, tokens, model)` became `unansweredCall(label, prompt,
  model)` so the transport no longer needs the token estimator; `ClaudeCallRecorder` estimates with the moved
  `PromptTokenEstimator` — same number booked.
- Configuration: `breakdownChunkSize` and `sectionRetries` moved from `AnthropicProperties` to
  `service.reports.claude.ClaudePromptProperties`, bound to the same `external.anthropic.*` keys (verified by the
  new wiring test). `service/pom.xml` declares `spring-boot-autoconfigure` (`@ConditionalOnExpression`).
  `application.yml` sets `CLAUDE_LOG_LEVEL` on the new package too.
- `ClaudeBatchPromptBuilder` sat at Checkstyle's 2,700-line limit; the added port import made it 2,701. One
  JavaDoc paragraph shortened by the rename was reflowed from three lines to two — no content change.
- New `application` test `ClaudeWiringSmokeTest` boots with an API key: both Claude flavours are
  `RealClaudeClient`, one `ClaudeTransport`, `RealLineItemMatchAssistant` active, prompt settings bound. The
  existing smoke test covers the no-key (stub) wiring.
- Tests: `service` 629 → 801, `external-services` 471 → 303, `application` 92 → 93 — the moved tests plus
  `ClaudeTransportBridgeTest` 4 and the wiring test; nothing lost. `mvn -Pmvp verify`: BUILD SUCCESS.
- **`service` imports in `external-services` main outside `bridge`: 0.** Gates: 3 of 21 failing (structure-lint
  on the module edge, D2/D3, D4) — the edge flip is the remaining step.

## 2026-11-12 — Anthropic transport converged (P8 / D6, branch `PDI_240`)

- Measured first: `anthropic` held 110 of the remaining `service` import lines. The transport,
  `AnthropicMessagesClient` (812 lines), is used by everything else through three methods (`callJsonObject`,
  `callJsonArray`, `callRaw`) and needed only `ClaudeUsageTracker` (`record`, `recordEstimated`),
  `ClaudeFailureLog` (`record`) and `ClaudeResponseNormalizer.extractText`. The rest — `ClaudeBatchPromptBuilder`
  2,700, `RealClaudeClient` 2,274, `EomPromptBuilder` 1,151, `ClaudeResponseNormalizer` 685, `StubClaudeClient`,
  flavour config, `RealLineItemMatchAssistant` — is prompt building and reply interpretation on ~40 service
  types: business logic, which the target shape keeps in `service`.
- This step (A1): new `anthropic.AnthropicClient` (public `callJsonObject`/`callJsonArray`/`callRaw`/`replyText`),
  implemented by `AnthropicMessagesClient`; new Anthropic-owned `AnthropicCallListener` (`usage`,
  `estimatedUsage`, `replyProblem`) replaces its two service collaborators; `bridge.ClaudeCallRecorder`
  implements it over `ClaudeUsageTracker` / `ClaudeFailureLog` (same calls, same arguments). Reply-text
  extraction moved from the normalizer into the transport (`replyText`); `RealClaudeClient`'s five raw-reply
  reads use it. `AnthropicMessagesClient` imports nothing from `service`.
- Tests: `AnthropicMessagesClientTest` 30 (29 constructor calls rewritten by an argument parser; the usage /
  no-usage / estimate tests now verify the listener; `replyText` test moved here from
  `ClaudeResponseNormalizerTest`), `RealClaudeClientTest` 47 (its five raw-reply stubs let the mocked
  transport's `replyText` run), new `ClaudeCallRecorderTest` 2. `external-services` 469 → 471.
  `mvn -Pmvp verify`: BUILD SUCCESS.
- `service` import lines in `external-services` main outside `bridge`: 110 in 11 files → 108 in 10 — all prompt
  and reply logic, the next step (A2: move it into `service` behind a transport port).

## 2026-11-12 — Charts and pacing pivots converged (P8 / D6, branch `PDI_240`)

- Measured first: pivoting the BigQuery export ran inside the integration — `TacticChartBuilder` and
  `SheetPacingTableWriter` called the service's `ChartPivot` (`parseBqHeaders`, `buildDailyPivot`,
  `buildMonthlyPivot`, `isMultiYear`) and `TacticLineItemGrouper` (in `external-services`, on the service's
  `LineItemMapping`). The chart request already had a "pivots supplied" mode (sheet flow). Pivot building is
  pure and lenient (rows it cannot use are skipped, nothing throws), so computing every tactic's pivots up
  front yields the same values.
- **Business calculation moved to the service:** `TacticLineItemGrouper` (+ test) `git mv`'d to
  `service.reports.engine`; new `TacticPivotAssembler` (+ `model.TacticPivots`, + test) computes daily and
  monthly pivots for tactics 1..N, or reports that the Date/Impressions columns are missing.
- The Google code only draws/writes pivots it is given: `google.deck.model.PacingPivot` (copy of `Pivot`),
  `PacingSeries` (fromSheet + per-tactic pivots), `TacticPacing` (columns-found flag, line item ids for the
  "no data" message, daily, monthly). `TacticChartBuilder` and `SheetPacingTableWriter` lost `ChartPivot`,
  `Headers`, `FlightDates` and the grouper; every message and its ordering is unchanged (the "BQ columns not
  found" checks still sit where they were — after the workbook-has-tabs check, after the monthly-placeholder
  check — driven by the flag).
- Chart vocabulary generated from the service sources into `google.charts` / `charts.model`:
  `SlidesChartClient` (was the `ChartProvider` port's shape), `TacticChartsRequest` (hand-written: pacing
  instead of BigQuery rows), `BreakdownChartsRequest`, `BreakdownChart`, `ChartSlice`, `PaidChartsRequest`,
  `PaidChart`, `DeckBreakdownSeries` (section is a `DeckBreakdown` — the Slides slice's by-name conversion in
  `BreakdownChartBuilder` is gone), `PaidChartKind`, `PaidChartShape`.
- Bridges: new `ChartProviderBridge` (only `ChartProvider`; BigQuery flow → `TacticPivotAssembler` over the
  flight window for both granularities, exactly as the builder did; sheet flow → pivots passed through, no
  pivoting) and `ChartModelMapper`. `SheetDeckProviderBridge.writePacingTables` pivots with the monthly window
  falling back to the flight window, as the writer did. The workbook slice's interim `WorkbookTabWriter`
  callback (`648cada`) is deleted: `RealSheetDeckProvider` calls `SheetPacingTableWriter` with a
  `WorkbookPacingRequest` again.
- `GoogleRequestRetrier`'s interrupt-only `AppException` is now `GoogleApiException`; both its callers sit
  behind bridges that translate it to the same `AppException(C000, message)`.
- Tests: new `TacticPivotAssemblerTest` 2, `ChartModelMapperTest` 4, `ChartProviderBridgeTest` 2; reworked
  `SheetDeckProviderBridgeTest` (pacing pivots, monthly-window fallback) 4; `SheetPacingTableWriterTest` +1
  (columns missing → skip without a Sheets call), `TacticChartBuilderTest` +2 (columns missing; empty pivot names
  the line items). Before this slice nothing exercised the pivot → chart/table hand-off. Chart builder, paid
  chart and style tests switched to the copies. Totals: `service` 624 → 629, `external-services` 462 → 469.
  `mvn -Pmvp verify`: BUILD SUCCESS.
- `service` import lines in `external-services` main outside `bridge`: 141 in 23 files → 110 in 11 — all in
  `anthropic`. `google`, `clerk` and `common` import nothing from `service`. Gates: **3 of 21 failing**
  (unchanged).

## 2026-11-12 — Workbook writer converged (P8 / D6, branch `PDI_240`)

- Measured first: `SheetDeckProvider` has 11 operations; `RealSheetDeckProvider` (1,935 lines) used 15
  service types — `BreakdownType`, ten breakdown-table records (`PublisherRow`, `CreativeTable`/`Row`,
  `GeoTable`/`Row`, `DeviceTable`/`Row`, `AudienceTable`/`AgeRow`/`SegmentRow`), `PacingTablesRequest`, the port
  and `AppException` (9, all `C000` + message).
- **Found:** the service's `ChartPivot` (a Spring bean that pivots BigQuery rows, with `SheetRowHelper`,
  `LineItemNamingHelper`, `ReportNumberParser`) is injected into `SheetPacingTableWriter` and
  `TacticChartBuilder` — business calculation inside the integration. Left for the chart slice, where the
  service will compute pivots and hand them over as data.
- New `google.sheets.SheetWorkbookClient` (generated from the port) and `google.sheets.model.Workbook*` (ten
  records generated from the service sources, `EMPTY` constants included; no service code compares them by
  identity). `RealSheetDeckProvider` / `StubSheetDeckProvider` implement it and import nothing from `service`;
  `BreakdownType` → `DeckBreakdown` (shared with Slides), `AppException` → `GoogleApiException`.
- Pacing tables: `writePacingTables(spreadsheetId, userToken, WorkbookTabWriter)` — the provider still builds
  the Sheets client for the caller, finds the first tab and returns a message on `IOException` exactly as
  before; the bridge passes a writer that calls `SheetPacingTableWriter.writeAll` with the service request.
  `SheetPacingTableWriter` left the provider's constructor and is injected into the bridge (it is an
  unconditional `@Component`).
- `bridge.SheetDeckProviderBridge` implements `SheetDeckProvider` (the only implementation);
  `bridge.WorkbookModelMapper` maps the tables back, keeping tactic order. Same `AppException(C000, message)`
  translation as the Slides bridge.
- Tests: `RealSheetDeckProviderTest` 38 (types switched, constructor without the pacing writer); new
  `WorkbookModelMapperTest` 2 (record components identical via reflection; every table maps) and
  `SheetDeckProviderBridgeTest` 3 (pacing callback, section/table mapping, error translation).
  `external-services` 457 → 462 tests. `mvn -Pmvp verify`: BUILD SUCCESS.
- `service` import lines in `external-services` main outside `bridge`: 164 in 25 files → 141 in 23. Gates:
  **3 of 21 failing** (unchanged).

## 2026-11-12 — Slides converged (P8 / D6, branch `PDI_240`)

- Measured first: `SlidesProvider` has 12 operations; `RealSlidesProvider` 1,975 lines used service types 61
  times (`BreakdownType` 32, `TemplateVariant` 11, `AppException` 13, `PaidSlidesFill`, the port,
  `BreakdownThoughtsGate`); six helpers used one service type each. Every `AppException` was `C000` + message.
- New Google-owned vocabulary in `google.deck` — copies, not moves, so the service keeps its own types:
  `DeckBreakdown` (same constants, order, codes, anchor labels — the workbook reads it by ordinal),
  `DeckTemplate`, `DeckMarkers` (U+2063 / `[[` / `]]`), `PaidDeckChannel`, `PaidDeckTableMarker`,
  `model/PaidDeckFill`, `model/PaidDeckTable`; plus `google.GoogleApiException`.
- `google.slides.SlidesDeckClient` (generated from the port, same JavaDoc) is implemented by
  `RealSlidesProvider` / `StubSlidesProvider`; they and `DeckMarkupRequestBuilder`, `EomSlideFinder`,
  `PaidSlidesWriter`, `MpoDeckPolicy`, `BreakdownSlideNaming`, `SlideModel` now import nothing from `service`.
  Kept in package `google` (shared package-private helpers with the workbook/chart code).
- The ">2 breakdowns" thoughts rule stays in the service: the bridge calls
  `BreakdownThoughtsGate.qualifyingTactics` and the client receives the qualifying tactic numbers
  (`addBreakdownSlides(..., Set<Integer> thoughtsTactics, ...)`, threaded through the
  `buildBreakdownRequests` overloads and the EOM path). Equivalent: `qualifyingTactics` keeps exactly the
  tactics for which `qualifies` is true.
- `bridge.SlidesProviderBridge` implements `SlidesProvider` (the only implementation); `bridge.DeckModelMapper`
  maps by constant name, keeping `null`s and map order. `GoogleApiException` → `AppException(C000, message)`:
  the job error text (`markJobFailed(jobId, ex.getMessage())`) is unchanged.
- Transitional: `BreakdownChartBuilder` converts `BreakdownType` → `DeckBreakdown` by name for the slide id
  until the charts are converted. `GoogleRequestRetrier` is unchanged (shared with the workbook writer and
  charts); its interrupt-only `AppException` passes through the bridge untouched.
- Tests: `RealSlidesProviderTest` 47 (unchanged count; gate tests now pass the qualifying set, the one
  cross-module contract test keeps the service `BreakdownType` for the service resolver stub),
  `EomSlideFinderTest`, `PaidSlidesWriterTest`, `MpoDeckPolicyTest`, `DeckMarkupRequestBuilderTest` switched to
  the deck types; new `DeckModelMapperTest` 6 (both vocabularies stay identical), `SlidesProviderBridgeTest` 4.
  `external-services` 447 → 457 tests. `mvn -Pmvp verify`: BUILD SUCCESS (`ApplicationSmokeTest` boots with the
  bridge as the single `SlidesProvider`).
- `service` import lines in `external-services` main outside `bridge`: 185 in 33 files → 164 in 25. Gates:
  **3 of 21 failing** (unchanged).

## 2026-11-12 — Sheets read path converged (P8 / D6, branch `PDI_240`)

- Scope: the read path only (`GoogleSheetsProvider` had one caller, `SheetQueryServiceImpl`). The
  workbook/deck writers (`RealSheetDeckProvider` ~1,900 lines, `SheetPacingTableWriter`,
  `ChartSheetWriter`) implement the deck/chart ports and move with the Slides/charts conversion.
- `externalservices.google.sheets` (no `service` imports): `SheetsClient` (was `GoogleSheetsProvider`,
  `git mv`), `SheetsExternalException` (HTTP status or `null` for transport + Google's message),
  `SheetsTabNotFoundException` (requested tab + visible tabs), `model/SheetsTabValues`,
  `impl/SheetsClientImpl` (was `RealGoogleSheetsProvider`), `impl/StubSheetsClient` (was
  `StubGoogleSheetsProvider`). Bean selection unchanged (`@Primary` + `@ConditionalOnBean`).
- `bridge/SheetQueryServiceImpl` (moved from `google`) now owns what was business logic in the
  providers: URL validation (C002, previously duplicated in real + stub), `SheetData` shaping (headers,
  5-row preview, counts — previously duplicated), and user-facing errors via new
  `bridge/SheetsFailureTranslator` (C001 with the visible-tabs parameter; C000 with the 403/404 hint
  for user vs service account, Google's message and status). Messages are byte-identical to before.
- Two incidental differences: tab matching lower-cases with `Locale.ROOT` (was the JVM default); a
  spreadsheet response with no `sheets` array now yields "tab not found" instead of a
  `NullPointerException`.
- Tests: there were none for this path. Added `SheetsClientImplTest` 4 (fake Google transport:
  hidden/visible tab resolution, empty tab, missing tab makes no values call, 403 mapping),
  `StubSheetsClientTest` 3, `SheetsFailureTranslatorTest` 5, `SheetQueryServiceImplTest` 7.
- Observed while testing: a JSON `null` cell would render as Google's `Data.NULL` sentinel's
  `toString()`. Pre-existing and not reachable from the real API (empty cells come back as `""` or are
  omitted); left unchanged.
- `service` import lines in `external-services` main outside `bridge`: 198 in 37 files → 185 in 33.
  `mvn -Pmvp verify`: BUILD SUCCESS. Gates: **3 of 21 failing** (unchanged).

## 2026-11-12 — Drive helpers converged (P8 / D6, branch `PDI_240`)

- `DriveCopier`, `DriveSharer`, `ChartFileSharer`, `DriveShareRecipients` and their tests moved to
  `externalservices.google.drive` (`git mv`, code unchanged apart from package/imports).
- The package imports nothing from `service`. The one link it had — admin emails for auto-share — now
  goes through the Drive-owned `DriveShareAudience` interface, implemented by
  `bridge.AdminDriveShareAudience` (delegates to `AdminEmailDirectory`). Recipients, order,
  normalisation and the never-throw guarantee are unchanged.
- Not done here, deliberately: the helpers still take the SDK `Drive` client. It is built per request
  from a user token or the service account by the Sheets/Slides/chart code; replacing it with a
  token-based `DriveClient` touches `RealSlidesProvider`/`RealSheetDeckProvider` (template copy) and
  the chart builders, so it moves with the Sheets/Slides conversion.
- `service` import lines in `external-services` main outside `bridge`: 200 in 39 files before the Clerk
  and Drive slices → 198 in 37 (the remainder is Sheets/Slides/charts and Anthropic; `bridge` holds 2).
- Tests: `AdminDriveShareAudienceTest` added; Drive tests 12 green. `mvn -Pmvp verify`: BUILD SUCCESS.
  Gates: **3 of 21 failing** (unchanged).

## 2026-11-12 — Clerk provider converged (P8 / D6, branch `PDI_240`)

- `externalservices.clerk` now has the onboarding-platform shape and **no `service` imports**:
  `ClerkClient` (`googleOAuthAccessTokens(clerkUserId)` → `List<ClerkOAuthAccessToken>`),
  `ClerkExternalException` (non-2xx, transport, unparseable body), `impl/ClerkClientImpl` (was
  `ClerkOAuthTokenService`, `git mv`), `model/ClerkOAuthAccessToken`, `config/ClerkProperties` +
  `config/ClerkConfig` (was `ExternalServicesClerkConfig`).
- New `externalservices.bridge.ClerkUserGoogleTokenProvider` implements the `UserGoogleTokenProvider`
  port: blank user id → no call; failure or no/blank token → warn + `null` (service-account fallback).
  Behaviour unchanged; the five consumers (`SheetQueryServiceImpl`, `SheetAdoptionServiceImpl`,
  `SheetSummaryQueryServiceImpl`, `DirectReportWorkflow`, `SlidesFromSheetWorkflow`) are untouched.
- Wiring unchanged: client and bridge exist only when `app.clerk.secret-key` is set. New
  `ClerkConfigTest` (`ApplicationContextRunner`) pins both cases — nothing tested the key-present
  wiring before. `spring-boot-test` declared at `test` scope for it (dependency analysis).
- Tests: `ClerkClientImplTest` 5 (bare array, `data` envelope, empty, 404, non-JSON),
  `ClerkUserGoogleTokenProviderTest` 5, `ClerkConfigTest` 2. `mvn -Pmvp verify`: BUILD SUCCESS.
- Gates: **3 of 21 failing** (unchanged; structure-lint still on the module edge, D6).

## 2026-11-12 — Module direction step 1 (P8 / D6, branch `PDI_240`)

- Measured before: `external-services` main code pulled a closure of 102 `service`/`domain` types; the
  only reach into `domain` and the admin services was `DriveShareRecipients` (`AdminAccessPolicy`,
  `AdminUserService`, `AdminUserEntity`).
- New `service.admin.services.AdminEmailDirectory` (+ `AdminEmailDirectoryImpl`) returns config admins
  then managed admins, degrading to config admins when the managed lookup fails (behaviour moved
  verbatim, test moved with it). `DriveShareRecipients` now depends on `GoogleProperties` +
  `AdminEmailDirectory` only and keeps normalisation/de-duplication.
- `external-services/pom.xml`: `domain` dependency removed; `dependency:analyze` clean.
- `mvn -Pmvp verify`: BUILD SUCCESS, all modules.
- structure-lint still stops at "application/pom.xml must not depend on external-services" — accepted
  as D6 until the per-provider convergence. Gates: **3 of 21 failing** (unchanged).
- Operational Hub was not available locally to compare against; onboarding-platform (same standard)
  is the reference for the target shape.

## 2026-11-12 — Service contract quality (P11, branch `PDI_240`)

- `ReportGenerationServiceImpl` 1,723 lines / 40 dependencies → 138 lines / 8. It keeps `start`,
  `enqueue`, `progress` and `run`, which opens/closes the usage and failure scopes and picks a workflow.
  Logic moved verbatim (JavaDoc included) into `service.reports.helpers` components:
  `DirectReportWorkflow` (build from the request's grids → SHEET or SLIDES), `SlidesFromSheetWorkflow`
  (build the deck from a reviewed workbook), `BreakdownSectionWorkflow` (read/gate/write the five
  breakdown sections, pacing series), `MpoDeckTokenWriter` (MPO/paid sections, EOM pacing narrative),
  `ClaudeCallDispatcher` (per-tactic fan-out on the virtual-thread executor), `ReportTokenAssembler`
  (placeholder shaping), `ReportRunRecorder` (slide count / token usage). One new method:
  `SlidesFromSheetWorkflow.sectionBullets`, extracted to keep `runSlidesFromSheet` under Checkstyle's
  150-line method limit.
- Earlier the same day: `SheetAdoptionServiceImpl` 9 → 7 deps (`AdoptedDraftRecorder`),
  `AdminStatsServiceImpl` 17 → 6 (`AdminStatsAssembler`, + `AdminStatsServiceImplTest`),
  `LineItemMatcherServiceImpl` 466 → ~210 lines (`TacticChannelCatalog`, `PlanLineIdMatcher`,
  `LineItemAiDisambiguator`).
- **Still heavy, not gated:** `SlidesFromSheetWorkflow` has 24 collaborators and 475 lines — the gate only
  measures `*ServiceImpl`. It is one pipeline, now readable on its own; splitting it further is optional.
- `ReportGenerationServiceImplTest` wires the real object graph over the same mocks and real collaborators
  (one shared usage tracker / failure log / executor); tests of moved methods call the collaborator.
- `check-service-contract-quality`: FAIL → PASS. **3 of 21 failing** (structure-lint: module direction;
  verify-gates: D2/D3; liquibase-preconditions: D4).
- `mvn verify`: BUILD SUCCESS, 1,206 tests (unchanged — no test lost), coverage gate met;
  `ApplicationSmokeTest` boots the context with the new beans.

## 2026-11-12 — Frontend UI rules (P12, branch `PDI_240`)

- 63 hex colours in `aidigital-tokens.css` / `base/tokens.css` rewritten as the exact `rgb()`
  equivalents (no rounding, colours unchanged); no hex remains in any CSS file.
- `reset.css`: `body { overflow-wrap: anywhere }`; `button` is centred `inline-flex` with
  `max-inline-size: 100%` / `min-inline-size: 0`. Audited all 54 button classes: 39 set no
  `display`; the two whose layout the reset would change got explicit rules — `.rc-typecard`
  (`display: block`, what it computes to today as a grid item) and `.ad-range__preset`
  (`justify-content: flex-start`, keeps its label left-aligned).
- Admin stat cards: `repeat(4, minmax(0, 1fr))` collapsing to 2 columns ≤ 56rem and 1 ≤ 30rem.
- Forms: add-admin input is `required` with `aria-invalid` / `aria-describedby` tied to the
  server error (`ErrorAlert` gained an optional `id`); the date-range form marks both dates
  `required`, flags a date later than today with `aria-invalid` + a linked `role="alert"`
  message, and disables Apply in that state (new behaviour). `DateRangePicker.test.tsx` covers
  the error and the valid path.
- **Not visually verified:** no browser session against the running app. Worth a quick look at
  the report-type cards, the date-range presets, button-heavy rows and the admin tables
  (`overflow-wrap: anywhere` lets auto-sized table columns shrink further than before).
- `check-frontend-ui-rules`: FAIL (77) → PASS. **4 of 21 failing.**
- Frontend: `typecheck`, `lint`, 138 tests (+2), `build` pass.

## 2026-11-12 — P10 API validation (branch `PDI_240`)

- **Contract hygiene** (no runtime change): 46 component object schemas closed with
  `additionalProperties: false`; 4 inline enums replaced by named schemas (`AdminReportSortV1`,
  `SortDirectionV1`, `AdminSeriesUnitV1`, `AdminTrendUnitV1` — the admin reports `sort`/`dir`
  query params are now typed enums, defaults unchanged because the resolver already defaults null);
  132 missing descriptions added (request bodies, schemas, properties).
- **Input constraints** (behaviour change — oversized input now 400): every sheet grid
  (`Rows2DV1`, `bqRows`, `planRows`) ≤ 200,000 rows, ≤ 702 cells/row (A..ZZ), ≤ 50,000 chars/cell;
  paid export ≤ 100 tabs; sheet URLs ≤ 2,048 (required ones ≥ 1); `tab` 1–100; `brief` ≤ 100,000;
  `changeLog` ≤ 20,000; `marketVolume` ≤ 100; `bqSheetId` ≤ 200; admin email `format: email`,
  ≤ 320 (also the `removeAdmin` path param). Verified in the generated models (`@Size`, `@Email`).
  The per-row cell bound is not emitted by the generator; the row and cell bounds are.
- `GlobalExceptionHandler` now answers 400 (C002), not 500, for `HttpMessageNotReadableException`
  (malformed JSON, unknown enum), `MethodArgumentTypeMismatchException` (e.g. `/report-jobs/abc`),
  `MissingServletRequestParameterException` and `HandlerMethodValidationException`
  (`@Min`/`@Max`/`@Size` on parameters).
- `ApiRequestValidationTest`: one `@WebMvcTest` slice over the six API controllers, 16 negative
  tests (one per constrained operation, two for the admin reports query) asserting 400 + `C002`.
- **Finding:** required array properties are not enforced — the generated models initialise them
  to an empty list, so a missing `planRows`/`adjRows` arrives as `[]` and passes `@NotNull`. Left as
  is; services already treat an empty grid as "no data".
- **Finding:** the spec says sheets are read `A1:Z`, the adapter reads `A1:ZZ`; limits follow the code.
- Gates: strict-schemas, documentation, enums, input-constraints, api-validation-tests FAIL → PASS.
  **5 of 21 failing.**
- `mvn verify`: BUILD SUCCESS, 1,203 tests (+16), 0 failures. Frontend: types regenerated,
  `typecheck`, `lint`, 136 tests and `build` pass.

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
