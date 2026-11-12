# AIAE Convergence — Migration Log

Measured facts only, newest first. Plan and scope: [`aiae-migration-plan.md`](./aiae-migration-plan.md).

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
