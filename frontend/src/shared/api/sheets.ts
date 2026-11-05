// Sheet-read helpers shared by the two wizard connect steps. Tab-name literals
// are load-bearing constants (exact spelling/case matters — see fidelity rules).
import { apiClient } from "./client";
import type { SheetReadResult, SheetSummaryResult } from "./types";

export const MEDIA_PLAN_PRIMARY_TAB = "Proposal";
// Workbooks without a "Proposal" tab (e.g. RFP exports) keep the media plan on the
// visible "Estimates" tab; fall back to it as the primary media-plan source.
export const MEDIA_PLAN_FALLBACK_TAB = "Estimates";
export const MEDIA_PLAN_OPTIONAL_TABS = ["Audience&Inventory", "Estimates", "Geo"] as const;
export const ELEVATE_TAB = "Basic";
// MPO performance-channel exports ("EOC report | raw data for automation | Search / Social"). Every export
// carries the daily "FULL-FLIGHT PERFORMANCE" tab; the breakdown tabs feed the Paid Search / Paid Social
// slides and are optional (a missing one only blanks the slide that needs it). Only the tabs the deck uses
// are read: the exports' device, geo and conversion-action tabs feed no slide yet, and the Meta export's
// CREATIVE BREAKDOWN tab is hidden (unreadable) — its rows are rebuilt from FULL-FLIGHT's Creative column.
export const PAID_EXPORT_REQUIRED_TAB = "FULL-FLIGHT PERFORMANCE";
export const PAID_SEARCH_EXPORT_TABS = [
    PAID_EXPORT_REQUIRED_TAB,
    "GROUP BREAKDOWN",
    "KEYWORD BREAKDOWN",
    "PMAX PLACEMENT BREAKDOWN",
    "AGE DISTRIBUTION",
    "GENDER SPLIT",
    "AD BREAKDOWN",
] as const;
export const PAID_SOCIAL_EXPORT_TABS = [
    PAID_EXPORT_REQUIRED_TAB,
    "PLACEMENT BREAKDOWN",
    "AUDIENCE TARGETING BREAKDOWN",
    "AGE DISTRIBUTION",
    "GENDER SPLIT",
] as const;

/** Legacy gate: "Pull data" is enabled only for a Google Sheets URL. */
export function isGoogleSheetUrl(url: string): boolean {
    return /docs\.google\.com\/spreadsheets/.test(url.trim());
}

/** Canonical Google Sheet URL from its id, so admins can reopen the source; undefined when no sheet. */
export function sheetUrlFromId(sheetId: string | undefined): string | undefined {
    return sheetId ? `https://docs.google.com/spreadsheets/d/${sheetId}` : undefined;
}

/**
 * Reads one tab via POST /sheets/read. Throws on transport/HTTP failure; on
 * success returns the dual ok/error contract body — the caller inspects
 * `ok`/`error` (e.g. `tab_not_found`) to drive the Estimates→Proposal fallback.
 */
export async function readSheetTab(url: string, tab: string): Promise<SheetReadResult> {
    const { data, error } = await apiClient.POST("/api/v1/sheets/read", {
        body: { url: url.trim(), tab },
    });
    if (error || !data) {
        // Surface the backend's real message (ApiErrorV1.message) instead of a
        // generic string, so the user sees *why* the read failed (no access,
        // bad link, tab missing, …) rather than "Failed to read tab".
        const backendMsg =
            (error as { message?: string } | undefined)?.message?.trim() ||
            (data as { message?: string } | undefined)?.message?.trim();
        throw new Error(
            backendMsg ||
                `Couldn't read the "${tab}" tab — check the sheet link and that you're signed in with a Google account that has at least Viewer access.`
        );
    }
    return data;
}

/**
 * Reads a generated report workbook back via POST /sheets/summary: the per-tactic summary
 * table (plan/fact units and spend) plus the workbook's own campaign-context cells. The
 * server locates everything on the workbook's first tab, so the caller only supplies the
 * workbook URL. Throws on transport/HTTP failure.
 */
export async function readSheetSummary(sheetUrl: string): Promise<SheetSummaryResult> {
    const { data, error } = await apiClient.POST("/api/v1/sheets/summary", {
        body: { sheetUrl: sheetUrl.trim() },
    });
    if (error || !data) {
        const backendMsg =
            (error as { message?: string } | undefined)?.message?.trim() ||
            (data as { message?: string } | undefined)?.message?.trim();
        throw new Error(backendMsg || "Couldn't read the generated sheet's summary table.");
    }
    return {
        rows: data.rows ?? [],
        rfpInfo: data.rfpInfo ?? null,
        changeLog: data.changeLog ?? null,
    };
}
