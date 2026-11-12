import { afterEach, describe, expect, it, vi } from "vitest";

import type { SheetReadResult } from "@/shared/api/types";
import {
    countDataRows,
    findHeaderRow,
    missingHeaders,
    paidExportPayload,
    PaidExportError,
    type PaidExport,
    readPaidExport,
    trimTrailingBlankRows,
} from "./paidExport";

const readSheetTab = vi.fn();
vi.mock("@/shared/api/sheets", async () => {
    const actual = await vi.importActual<typeof import("@/shared/api/sheets")>("@/shared/api/sheets");
    return { ...actual, readSheetTab: (url: string, tab: string) => readSheetTab(url, tab) };
});

const URL = "https://docs.google.com/spreadsheets/d/ads1/edit";
// FULL-FLIGHT as the Google Ads export lays it out: header row, a zero "totals" row without a date, daily rows.
const HEADER = ["Table Info", "Date", "Channel", "Level 1 Naming", "Impressions", "Clicks", "Cost", "Conversions"];
const FULL_FLIGHT = [
    HEADER,
    ["", "", "", "", "0.0", "0.0", "0.0", "0.0"],
    ["", "46302", "Google Search", "Brand", "59", "6", "34.57", "3.81"],
    ["", "46302", "Google Search", "NonBrand", "17", "1", "4.44", "1.39"],
];

/**
 * Builds a sheet read result, overriding what the test is about.
 *
 * @param over the fields under test
 * @returns the read result
 */
function sheetRead(over: Partial<SheetReadResult> = {}): SheetReadResult {
    return {
        ok: true,
        tab: "FULL-FLIGHT PERFORMANCE",
        title: "EOC report | raw data for automation | Search",
        sheetId: "ads1",
        rows: 4,
        cols: 8,
        tabs: ["FULL-FLIGHT PERFORMANCE"],
        headers: HEADER,
        preview: [],
        rawRows: FULL_FLIGHT,
        ...over,
    } as SheetReadResult;
}

describe("trimTrailingBlankRows", () => {
    it("should drop only the trailing all-blank rows test", () => {
        // Given: a blank row inside the data and padding after it
        const rows = [["a"], ["", " "], ["b"], [""], ["", ""]];

        // When
        const trimmed = trimTrailingBlankRows(rows);

        // Then
        expect(trimmed).toEqual([["a"], ["", " "], ["b"]]);
    });
});

describe("findHeaderRow", () => {
    it("should find the header below a title row, case-insensitively test", () => {
        // Given
        const rows = [["FULL-FLIGHT PERFORMANCE"], ["DATE", " impressions ", "Clicks", "COST", "Conversions"]];

        // When / Then
        expect(findHeaderRow(rows)).toBe(1);
    });

    it("should return -1 when a required header is missing test", () => {
        // Given: no Conversions column
        const rows = [["Date", "Impressions", "Clicks", "Cost"]];

        // When / Then
        expect(findHeaderRow(rows)).toBe(-1);
        expect(missingHeaders(rows)).toEqual(["conversions"]);
    });
});

describe("countDataRows", () => {
    it("should count only rows that carry a date test", () => {
        // Given / When / Then: the zero totals row has no date
        expect(countDataRows(FULL_FLIGHT, 0)).toBe(2);
    });
});

describe("readPaidExport", () => {
    afterEach(() => {
        readSheetTab.mockReset();
    });

    it("should read FULL-FLIGHT and every present breakdown tab test", async () => {
        // Given: the workbook has the keyword tab (spelled in a different case) but no group tab
        readSheetTab.mockImplementation(async (_url: string, tab: string) =>
            tab === "FULL-FLIGHT PERFORMANCE"
                ? sheetRead({ tabs: ["FULL-FLIGHT PERFORMANCE", "Keyword Breakdown", "Notes"] })
                : sheetRead({ tab, rawRows: [["KEYWORD BREAKDOWN"], ["Keyword", "Clicks"], ["velmora", "204"], [""]] })
        );

        // When
        const exp = await readPaidExport(URL, ["FULL-FLIGHT PERFORMANCE", "GROUP BREAKDOWN", "KEYWORD BREAKDOWN"]);

        // Then
        expect(readSheetTab.mock.calls).toEqual([
            [URL, "FULL-FLIGHT PERFORMANCE"],
            [URL, "Keyword Breakdown"],
        ]);
        expect(Object.keys(exp.tabs)).toEqual(["FULL-FLIGHT PERFORMANCE", "Keyword Breakdown"]);
        expect(exp.tabs["Keyword Breakdown"]).toEqual([["KEYWORD BREAKDOWN"], ["Keyword", "Clicks"], ["velmora", "204"]]);
        expect(exp.missingTabs).toEqual(["GROUP BREAKDOWN"]);
        expect(exp.dataRows).toBe(2);
        expect(exp.title).toBe("EOC report | raw data for automation | Search");
        expect(exp.sheetId).toBe("ads1");
    });

    it("should report a breakdown tab whose read fails as missing test", async () => {
        // Given
        readSheetTab.mockImplementation(async (_url: string, tab: string) =>
            tab === "FULL-FLIGHT PERFORMANCE"
                ? sheetRead({ tabs: ["FULL-FLIGHT PERFORMANCE", "AD BREAKDOWN"] })
                : sheetRead({ ok: false, error: "read_failed", tab })
        );

        // When
        const exp = await readPaidExport(URL, ["FULL-FLIGHT PERFORMANCE", "AD BREAKDOWN"]);

        // Then
        expect(Object.keys(exp.tabs)).toEqual(["FULL-FLIGHT PERFORMANCE"]);
        expect(exp.missingTabs).toEqual(["AD BREAKDOWN"]);
    });

    it("should reject a workbook without a FULL-FLIGHT tab test", async () => {
        // Given
        readSheetTab.mockResolvedValue(sheetRead({ ok: false, error: "tab_not_found", tabs: ["Basic"] }));

        // When
        const read = readPaidExport(URL, ["FULL-FLIGHT PERFORMANCE"]);

        // Then
        await expect(read).rejects.toBeInstanceOf(PaidExportError);
        await expect(read).rejects.toThrow('No "FULL-FLIGHT PERFORMANCE" tab in that workbook');
    });

    it("should reject a FULL-FLIGHT tab without the required columns test", async () => {
        // Given
        readSheetTab.mockResolvedValue(sheetRead({ rawRows: [["Date", "Impressions", "Clicks"], ["46302", "5", "0"]] }));

        // When
        const read = readPaidExport(URL, ["FULL-FLIGHT PERFORMANCE"]);

        // Then
        await expect(read).rejects.toThrow('"FULL-FLIGHT PERFORMANCE" is missing the cost, conversions column(s).');
        expect(readSheetTab).toHaveBeenCalledTimes(1);
    });
});

describe("paidExportPayload", () => {
    const search: PaidExport = {
        title: "Search",
        sheetId: "ads1",
        tabs: { "FULL-FLIGHT PERFORMANCE": FULL_FLIGHT },
        missingTabs: [],
        dataRows: 2,
    };

    it("should send the connected exports' tabs and links for an MPO deck test", () => {
        // Given / When
        const payload = paidExportPayload("MPO", { search, social: null });

        // Then: only Paid Search is present
        expect(payload).toEqual({
            paidSearchTabs: { "FULL-FLIGHT PERFORMANCE": FULL_FLIGHT },
            paidSocialTabs: undefined,
            paidSearchUrl: "https://docs.google.com/spreadsheets/d/ads1",
            paidSocialUrl: undefined,
        });
    });

    it("should send nothing for a standard deck even with an export connected test", () => {
        // Given / When
        const payload = paidExportPayload("STANDARD", { search, social: search });

        // Then
        expect(payload).toEqual({});
    });
});
