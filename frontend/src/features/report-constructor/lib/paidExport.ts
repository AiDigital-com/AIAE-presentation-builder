// Reads a paid-media export workbook (Google Ads / Meta "raw data for automation") for the MPO deck's Paid
// Search and Paid Social sections. The export is many tabs, not one: the daily FULL-FLIGHT PERFORMANCE tab
// is required and validated; the breakdown tabs are read when present and reported as missing otherwise.
import { PAID_EXPORT_REQUIRED_TAB, readSheetTab, sheetUrlFromId } from "@/shared/api/sheets";
import type { GenerateRequest, Rows2D, TemplateVariant } from "@/shared/api/types";

/** Export tabs keyed by the tab name as the workbook spells it. */
export type PaidExportTabs = Record<string, Rows2D>;

/** One connected export: its tabs plus what the connect row shows. */
export interface PaidExport {
    title: string;
    sheetId: string;
    tabs: PaidExportTabs;
    /** Breakdown tabs the deck uses that the workbook does not have (their slides stay blank). */
    missingTabs: string[];
    /** FULL-FLIGHT PERFORMANCE rows that carry a date — the export's real daily rows. */
    dataRows: number;
}

/** A link that is readable but is not a usable export; the message is shown to the user as-is. */
export class PaidExportError extends Error {}

/** FULL-FLIGHT headers the deck cannot do without (matched trimmed and case-insensitively). */
export const PAID_EXPORT_REQUIRED_HEADERS = ["date", "impressions", "clicks", "cost", "conversions"] as const;

/** How many top rows are searched for the FULL-FLIGHT header row. */
const HEADER_SCAN_ROWS = 5;

const norm = (s: string | undefined) => (s ?? "").trim().toLowerCase();

/**
 * Drops trailing rows whose cells are all blank (exports pad their tabs with thousands of them).
 *
 * @param rows the tab grid
 * @returns the grid without trailing blank rows
 */
export function trimTrailingBlankRows(rows: Rows2D): Rows2D {
    let end = rows.length;
    while (end > 0 && rows[end - 1].every((c) => norm(c) === "")) end--;
    return rows.slice(0, end);
}

/**
 * Finds the header row of a FULL-FLIGHT tab: the first of the top rows that holds every required header.
 *
 * @param rows the tab grid
 * @returns the row index, or -1 when no top row has them all
 */
export function findHeaderRow(rows: Rows2D): number {
    for (let i = 0; i < Math.min(rows.length, HEADER_SCAN_ROWS); i++) {
        const cells = rows[i].map(norm);
        if (PAID_EXPORT_REQUIRED_HEADERS.every((h) => cells.includes(h))) return i;
    }
    return -1;
}

/**
 * Lists the required headers missing from the most complete of the top rows, for the error message.
 *
 * @param rows the tab grid
 * @returns the missing header names
 */
export function missingHeaders(rows: Rows2D): string[] {
    let best: string[] = [...PAID_EXPORT_REQUIRED_HEADERS];
    for (const row of rows.slice(0, HEADER_SCAN_ROWS)) {
        const cells = row.map(norm);
        const missing = PAID_EXPORT_REQUIRED_HEADERS.filter((h) => !cells.includes(h));
        if (missing.length < best.length) best = missing;
    }
    return best;
}

/**
 * Counts the daily rows under the header: rows with a non-blank Date cell (the exports' zero totals row
 * right under the header has none).
 *
 * @param rows the tab grid
 * @param headerRow the header row index
 * @returns the number of dated rows
 */
export function countDataRows(rows: Rows2D, headerRow: number): number {
    const dateCol = rows[headerRow].map(norm).indexOf("date");
    return rows.slice(headerRow + 1).filter((r) => norm(r[dateCol]) !== "").length;
}

/**
 * Reads a paid-media export: FULL-FLIGHT PERFORMANCE first (required, header-checked), then every other
 * wanted tab the workbook has, in parallel. Tab names are matched trimmed and case-insensitively; the
 * workbook's own spelling is kept as the key.
 *
 * @param url the export's Google Sheet link
 * @param wantedTabs the tabs this channel's slides use, FULL-FLIGHT included
 * @returns the export
 * @throws PaidExportError when the workbook is not a usable export; other errors when the read fails
 */
export async function readPaidExport(url: string, wantedTabs: readonly string[]): Promise<PaidExport> {
    const main = await readSheetTab(url, PAID_EXPORT_REQUIRED_TAB);
    if (!main.ok) {
        if (main.error === "tab_not_found") {
            throw new PaidExportError(
                `No "${PAID_EXPORT_REQUIRED_TAB}" tab in that workbook — connect the export sheet itself.`
            );
        }
        throw new Error(main.error || "Could not read sheet");
    }
    const mainRows = trimTrailingBlankRows(main.rawRows);
    const headerRow = findHeaderRow(mainRows);
    if (headerRow < 0) {
        throw new PaidExportError(
            `"${PAID_EXPORT_REQUIRED_TAB}" is missing the ${missingHeaders(mainRows).join(", ")} column(s).`
        );
    }

    const present = new Map(main.tabs.map((t) => [norm(t), t]));
    const optional = wantedTabs.filter((t) => norm(t) !== norm(PAID_EXPORT_REQUIRED_TAB));
    const found = optional.flatMap((t) => (present.has(norm(t)) ? [present.get(norm(t)) as string] : []));
    const reads = await Promise.all(found.map((t) => readSheetTab(url, t)));

    const tabs: PaidExportTabs = { [main.tab]: mainRows };
    reads.forEach((r, i) => {
        if (r.ok) tabs[found[i]] = trimTrailingBlankRows(r.rawRows);
    });
    const missingTabs = optional.filter((t) => !Object.keys(tabs).some((k) => norm(k) === norm(t)));

    return {
        title: main.title ?? "",
        sheetId: main.sheetId ?? "",
        tabs,
        missingTabs,
        dataRows: countDataRows(mainRows, headerRow),
    };
}

/** The generate/preview request fields that carry the paid exports. */
export type PaidExportPayload = Pick<GenerateRequest, "paidSearchTabs" | "paidSocialTabs" | "paidSearchUrl" | "paidSocialUrl">;

/**
 * The paid-export part of a generate/preview request. The exports only exist on the MPO design: a standard
 * deck never carries them, even if the user connected one before switching back to the standard tab.
 *
 * @param variant the deck's template variant
 * @param channels the connected exports per channel (null when not connected)
 * @returns the request fields, empty for a standard deck
 */
export function paidExportPayload(
    variant: TemplateVariant,
    channels: { search: PaidExport | null; social: PaidExport | null }
): PaidExportPayload {
    if (variant !== "MPO") return {};
    return {
        paidSearchTabs: channels.search?.tabs,
        paidSocialTabs: channels.social?.tabs,
        paidSearchUrl: sheetUrlFromId(channels.search?.sheetId),
        paidSocialUrl: sheetUrlFromId(channels.social?.sheetId),
    };
}
