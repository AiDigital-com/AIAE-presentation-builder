import type { MappingEntry, ReportType, Rows2D, TemplateVariant } from "@/shared/api/types";

export interface ConnectedSheet {
    title: string;
    tab: string;
    sheetId: string;
    rows: number;
    cols: number;
    tabsCount: number;
    headers: string[];
    preview: string[][];
}

export interface MediaPlanState extends ConnectedSheet {
    sheetRows: Rows2D; // Proposal (primary)
    audienceRows: Rows2D; // Audience&Inventory (optional)
    estimatesRows: Rows2D; // Estimates (optional, falls back to Proposal)
    geoRows: Rows2D; // all workbook tabs bundled (each prefixed "### TAB: <name> ###") for Claude geo extraction
}

export interface ElevateState extends ConnectedSheet {
    adjRows: Rows2D; // Basic; sheetId doubles as bqSheetId (presence gate)
}

// MPO performance channels, each fed by an optional sheet export (Google Ads / Meta).
export type PaidChannel = "search" | "social";

export interface PaidChannelState {
    title: string;
    sheetId: string;
    // Export tabs keyed by the workbook's own tab name (FULL-FLIGHT PERFORMANCE always present).
    tabs: Record<string, Rows2D>;
    // Breakdown tabs the deck uses that this export lacks; their slides stay blank.
    missingTabs: string[];
    // Dated FULL-FLIGHT rows, shown on the connect row.
    dataRows: number;
}

export type OptionalTabs = Pick<MediaPlanState, "audienceRows" | "estimatesRows" | "geoRows">;

export interface WizardContextValue {
    brief: string;
    changeLog: string;
    reportType: ReportType;
    // Deck design, picked by the template tab on step 1. Independent of the report type and of the
    // connected sheets, so switching it never invalidates anything entered later in the wizard.
    templateVariant: TemplateVariant;
    marketVolume: string;
    // When false the per-tactic dayparting (weekdays/weekends) and gender split (male/female) are written
    // to the deck as an em-dash instead of being estimated by Claude — those DSP metrics aren't always
    // tracked reliably, so a data-sensitive client can fill them by hand instead of showing a prediction.
    estimateDaypartGender: boolean;
    mediaPlan: MediaPlanState | null;
    elevate: ElevateState | null;
    // Optional MPO-only exports. Independent of the media plan and Elevate, so connecting one never
    // invalidates the line-item match; kept when the user switches tabs, but only sent for MPO.
    paidChannels: Record<PaidChannel, PaidChannelState | null>;
    mapping: MappingEntry[] | null;
    // Media-plan rows the user dropped on the matching screen (by their original tacticNum). A media
    // plan often carries line items this report is not about; a dropped row keeps its place in
    // `mapping` — so the plan-order figures stay aligned and the drop stays undoable — but is absent
    // from `activeMapping`, which is what every later step and the generate payload are built from.
    excludedTactics: number[];
    /** The tactics the report actually covers: `mapping` minus the excluded rows, in plan order. */
    activeMapping: MappingEntry[];
    matchConfirmed: boolean;
    // EOM pacing (monthly budget + buy type + final rate per tactic) is confirmed in its own
    // dialog, separately from the line-item mapping, so editing one never re-opens the other.
    pacingConfirmed: boolean;
    // Flight window confirmed by the user, derived from the raw-data ("Basic" tab)
    // date range. Dates are ISO yyyy-MM-dd; the media plan is never used for dates.
    dateStart: string;
    dateEnd: string;
    dateConfirmed: boolean;
    // True once the user typed a date themselves — suggestions (EOM's previous-month
    // default, the Elevate detection) never overwrite a hand-picked window.
    datesEdited: boolean;

    setBrief(value: string): void;
    setChangeLog(value: string): void;
    setReportType(value: ReportType): void;
    setTemplateVariant(value: TemplateVariant): void;
    setMarketVolume(value: string): void;
    setEstimateDaypartGender(value: boolean): void;
    connectMediaPlan(value: MediaPlanState): void;
    updateMediaPlanTabs(patch: Partial<OptionalTabs>): void;
    disconnectMediaPlan(): void;
    connectElevate(value: ElevateState): void;
    disconnectElevate(): void;
    connectPaidChannel(channel: PaidChannel, value: PaidChannelState): void;
    disconnectPaidChannel(channel: PaidChannel): void;
    setMapping(mapping: MappingEntry[]): void;
    /** Drops a media-plan row from the report, or puts it back; identified by its original tacticNum. */
    toggleTacticExcluded(tacticNum: number): void;
    confirmMatch(): void;
    resetMatch(): void;
    /** Writes the pacing fields (monthly budget, rate type, unit price) without touching the confirmed mapping. */
    setPacing(mapping: MappingEntry[]): void;
    confirmPacing(): void;
    setDateWindow(start: string, end: string): void;
    suggestDateWindow(start: string, end: string): void;
    confirmDates(): void;
    resetDates(): void;
}
