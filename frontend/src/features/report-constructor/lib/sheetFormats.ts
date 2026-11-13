/**
 * What each source sheet on the "Campaign data" step has to look like, shown to the user next to the
 * link field so a wrong workbook is caught before it is connected rather than after a failed read.
 *
 * Every rule here mirrors a reader in the code (frontend `sheets.ts` / `mediaPlanBudget.ts`, backend
 * `CampaignDataCollector`, `LineItemMatcherServiceImpl`, `SheetPlaceholderReaderImpl`); change both
 * together. The example links are reference workbooks that already have the expected layout.
 */
export interface SheetFormat {
    /** Short heading shown on the collapsed toggle. */
    summary: string;
    /** One requirement per bullet, in the order a user would check them. */
    rules: readonly string[];
    /** A workbook in the expected format. */
    exampleUrl: string;
}

export type SheetFormatKey = "mediaPlan" | "elevate" | "reportSheet" | "paidSearch" | "paidSocial";

export const SHEET_FORMATS: Record<SheetFormatKey, SheetFormat> = {
    mediaPlan: {
        summary: "Expected media plan format",
        rules: [
            'Standard AI Digital media-plan template, with a tab named "Proposal" (or "Estimates"). Otherwise you\'ll be asked to pick the tab.',
            'A header row with a "Media" column and budget columns: Total cost / Cost / Budget and Impressions. Clicks, CTR, VCR, Reach, Rate type, Units, Unit price and Flight start / Flight end are read when present.',
            "One row per tactic, with a standard tactic name in the Media column (e.g. Programmatic Display, Programmatic CTV, Meta (CPM), Google SEM). Unknown names, sub-totals and added-value rows are skipped.",
            'A "Line ID" column (or Line # / Placement ID / ID) lets tactics auto-match to their line items. It must hold the same ID as the line item names.',
            "Shared with your Google account (Viewer access is enough).",
        ],
        exampleUrl:
            "https://docs.google.com/spreadsheets/d/1aXD4R1Supm1oqbg82LWOa9R533g4GHPrqw0ftGlP9AU/edit?gid=909269000#gid=909269000",
    },
    elevate: {
        summary: "Expected Elevate data format",
        rules: [
            'An Elevate DataSheet with a tab named "Basic" holding the raw delivery data. Campaigns without an Elevate dashboard need one built first.',
            "Headers in row 1: Date, Channel, Cost, Impressions and Level 1 Naming. Clicks, Completions, Line Item ID and Creative are read when present.",
            "Level 1 Naming follows the naming convention: the 3rd underscore-separated part is the media plan's Line ID, the 9th is the line item ID (digits), e.g. …_CPM_-_616641_Contextual. A wrong naming convention means the line item can't be matched.",
            "Dates like 2026-06-03, 6/3/2026 or Jun 3, 2026. Flight dates are detected from them.",
        ],
        exampleUrl:
            "https://docs.google.com/spreadsheets/d/1EJK9HTT_eIjmZrwt83WupDexJ-BAzT-qCpRuwjXIitY/edit?gid=2133572115#gid=2133572115",
    },
    reportSheet: {
        summary: "Expected report sheet format",
        rules: [
            "Follows the report template. Only the first tab is read for the campaign and its tactics.",
            'A summary table with a "Tactic name" column, one tactic per row down to "Total", plus Plan / Fact columns (Impressions, Clicks, Spend, CTR, VCR…).',
            'Info cells such as "Client name:", "Campaign name:" and "Flight dates:", each with its value in the cell to the right.',
            'Optional "Breakdowns" tab with Top Publishers, Creative analysis, Geo analysis, Audience analysis and Device breakdown blocks.',
        ],
        exampleUrl:
            "https://docs.google.com/spreadsheets/d/1V929E18woJxP_UJs7TEWZOI2NTJTBbIVCSz5wAqEh-g/edit?gid=65111710#gid=65111710",
    },
    paidSearch: {
        summary: "Expected Paid Search export format",
        rules: [
            'The Google Ads "EOC report | raw data for automation | Search" export. Connect the whole workbook, not a single tab.',
            'A "FULL-FLIGHT PERFORMANCE" tab is required. One of its top 5 rows holds the headers Date, Impressions, Clicks, Cost and Conversions, in any order, with one row per day below it.',
            "Breakdown tabs: GROUP BREAKDOWN, KEYWORD BREAKDOWN, PMAX PLACEMENT BREAKDOWN, AGE DISTRIBUTION, GENDER SPLIT and AD BREAKDOWN. Each is optional. If one is missing, only the slide built from it stays empty.",
            "Tab names must match the export's spelling. Case and extra spaces don't matter. Device, geo and conversion-action tabs are not used yet.",
            "Shared with your Google account (Viewer access is enough).",
        ],
        exampleUrl:
            "https://docs.google.com/spreadsheets/d/131JK0wkvZX2SKSOzNkYFuZEpo2Lr691v8wLEgQfSlTw/edit?gid=2018742232#gid=2018742232",
    },
    paidSocial: {
        summary: "Expected Paid Social export format",
        rules: [
            'The Meta "EOC report | raw data for automation | Social" export. Connect the whole workbook, not a single tab.',
            'A "FULL-FLIGHT PERFORMANCE" tab is required. One of its top 5 rows holds the headers Date, Impressions, Clicks, Cost and Conversions, in any order, with one row per day below it.',
            "Keep the Creative column in FULL-FLIGHT PERFORMANCE. The creative slide is built from it, so a hidden CREATIVE BREAKDOWN tab doesn't matter.",
            "Breakdown tabs: PLACEMENT BREAKDOWN, AUDIENCE TARGETING BREAKDOWN, AGE DISTRIBUTION and GENDER SPLIT. Each is optional. If one is missing, only the slide built from it stays empty.",
            "Shared with your Google account (Viewer access is enough).",
        ],
        exampleUrl:
            "https://docs.google.com/spreadsheets/d/10i_KCC2ZgT-lVGxzddIzLb9e9bMGaHUup9ggpQKHRoQ/edit?gid=1001034757#gid=1001034757",
    },
};
