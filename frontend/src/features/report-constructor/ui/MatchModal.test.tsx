import { useEffect } from "react";
import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, within } from "@testing-library/react";
import type { LineItemMatchResult, MappingEntry } from "@/shared/api/types";
import { useWizard, WizardProvider } from "@/shared/wizard/WizardContext";
import { MatchModal } from "./MatchModal";

const P = "GCVB_Grapevine Convention & Visitors Bureau_";

/**
 * Builds a match result whose ids carry the given channel; namings follow the real Level 1 pattern.
 */
function matchResult(mapping: MappingEntry[], ids: Record<string, string>): LineItemMatchResult {
    const idNamings: LineItemMatchResult["idNamings"] = {};
    for (const [id, channel] of Object.entries(ids)) {
        idNamings[id] = {
            naming: `${P}24_Main Street Fest 2026_${channel}_Prospecting_CPM_-_${id}_Audience`,
            channel,
            tactic: "Prospecting",
        };
    }
    return {
        mapping,
        uniqueIds: Object.keys(ids),
        idNamings,
        channelToIds: {},
        warnings: [],
        autoMatched: false,
        autoMatchCount: 0,
    };
}

/**
 * Seeds the wizard (media plan first — connecting one resets the mapping — then the mapping) once,
 * and prints which id each tactic holds.
 */
function MappingSeed({ mapping, planRows }: { mapping: MappingEntry[]; planRows?: string[][] }) {
    const { mapping: current, setMapping, connectMediaPlan } = useWizard();
    useEffect(() => {
        if (planRows) {
            connectMediaPlan({
                title: "Plan",
                tab: "Proposal",
                sheetId: "plan-sheet",
                rows: planRows.length,
                cols: 0,
                tabsCount: 1,
                headers: [],
                preview: [],
                sheetRows: planRows,
                audienceRows: [],
                estimatesRows: [],
                geoRows: [],
            });
        }
        setMapping(mapping);
    }, []);
    return (
        <output data-testid="mapping">
            {(current ?? []).map((m) => `${m.tacticName}=${m.lineItemId ?? ""}`).join("|")}
        </output>
    );
}

/**
 * Renders the open modal inside a fresh wizard seeded with the mapping.
 */
function renderModal(mapping: MappingEntry[], data: LineItemMatchResult, planRows?: string[][]) {
    render(
        <WizardProvider>
            <MappingSeed mapping={mapping} planRows={planRows} />
            <MatchModal
                open
                matchData={data}
                running={false}
                onClose={vi.fn()}
                onRun={vi.fn()}
                onConfirm={vi.fn()}
            />
        </WizardProvider>
    );
}

/**
 * The tactic rows currently rendered, by tactic name.
 */
function visibleTactics(): string[] {
    return Array.from(document.querySelectorAll(".match-tactic-name")).map((el) => el.textContent ?? "");
}

/**
 * The line item ids currently rendered in the pool.
 */
function visibleIds(): string[] {
    return Array.from(document.querySelectorAll(".match-id-card-num")).map((el) => el.textContent ?? "");
}

describe("MatchModal — search", () => {
    it("should show only the tactics whose name matches the search test", () => {
        // Given
        const mapping: MappingEntry[] = [
            { tacticNum: 1, tacticName: "Programmatic Display" },
            { tacticNum: 2, tacticName: "CTV Precision Reach" },
            { tacticNum: 3, tacticName: "Meta (CPM)" },
        ];
        renderModal(mapping, matchResult(mapping, { "650115": "Display" }));

        // When
        fireEvent.change(screen.getByRole("searchbox", { name: "Search tactics" }), {
            target: { value: "ctv" },
        });

        // Then
        expect(visibleTactics()).toEqual(["CTV Precision Reach"]);
        expect(screen.getByText(/1 shown/)).toBeTruthy();
    });

    it("should find a tactic by the line item id matched to it test", () => {
        // Given: Meta already holds 650120
        const mapping: MappingEntry[] = [
            { tacticNum: 1, tacticName: "Programmatic Display" },
            { tacticNum: 2, tacticName: "Meta (CPM)", lineItemId: "650120" },
        ];
        renderModal(mapping, matchResult(mapping, { "650115": "Display", "650120": "Meta" }));

        // When
        fireEvent.change(screen.getByRole("searchbox", { name: "Search tactics" }), {
            target: { value: "650120" },
        });

        // Then
        expect(visibleTactics()).toEqual(["Meta (CPM)"]);
    });

    it("should filter line items by channel and by every word typed test", () => {
        // Given
        const mapping: MappingEntry[] = [{ tacticNum: 1, tacticName: "Programmatic Display" }];
        renderModal(
            mapping,
            matchResult(mapping, { "650115": "Display", "650124": "CTV", "650128": "DOOH", "650133": "TikTok" })
        );
        const search = screen.getByRole("searchbox", { name: "Search line items" });

        // When
        fireEvent.change(search, { target: { value: "dooh" } });
        const byChannel = visibleIds();
        fireEvent.change(search, { target: { value: "ctv 650124" } });
        const byTwoWords = visibleIds();
        fireEvent.change(search, { target: { value: "ctv 650133" } });
        const contradictory = visibleIds();

        // Then
        expect(byChannel).toEqual(["650128"]);
        expect(byTwoWords).toEqual(["650124"]);
        expect(contradictory).toEqual([]);
        expect(screen.getByText("No line items match “ctv 650133”")).toBeTruthy();
    });

    it("should bring every item back when the search is cleared test", () => {
        // Given
        const mapping: MappingEntry[] = [
            { tacticNum: 1, tacticName: "Programmatic Display" },
            { tacticNum: 2, tacticName: "TikTok (CPM)" },
        ];
        renderModal(mapping, matchResult(mapping, { "650115": "Display", "650133": "TikTok" }));
        fireEvent.change(screen.getByRole("searchbox", { name: "Search tactics" }), {
            target: { value: "nothing like this" },
        });
        fireEvent.change(screen.getByRole("searchbox", { name: "Search line items" }), {
            target: { value: "tiktok" },
        });
        expect(screen.getByText("No tactics match “nothing like this”")).toBeTruthy();

        // When
        fireEvent.click(screen.getByRole("button", { name: "Clear search tactics" }));
        fireEvent.keyDown(screen.getByRole("searchbox", { name: "Search line items" }), { key: "Escape" });

        // Then
        expect(visibleTactics()).toEqual(["Programmatic Display", "TikTok (CPM)"]);
        expect(visibleIds()).toEqual(["650115", "650133"]);
        expect(screen.queryByRole("button", { name: "Clear search tactics" })).toBeNull();
    });

    it("should show each tactic's plan line id and geo, and find tactics by them test", () => {
        // Given: two Programmatic Display lines told apart only by line id and geo
        const mapping: MappingEntry[] = [
            { tacticNum: 1, tacticName: "Programmatic Display" },
            { tacticNum: 2, tacticName: "Programmatic Display" },
        ];
        const planRows = [
            ["Line ID", "Geo", "Media", "Rate Type", "Units", "Unit Price"],
            ["PL-7", "Grapevine (TX)", "Programmatic Display", "CPM", "250000", "6"],
            ["PL-8", "DFW DMA", "Programmatic Display", "CPM", "100000", "6"],
        ];
        renderModal(mapping, matchResult(mapping, { "650115": "Display" }), planRows);
        expect(screen.getByText("ID PL-7")).toBeTruthy();
        expect(screen.getByText("Grapevine (TX)")).toBeTruthy();
        expect(screen.getByText("ID PL-8")).toBeTruthy();
        expect(screen.getByText("DFW DMA")).toBeTruthy();

        // When
        fireEvent.change(screen.getByRole("searchbox", { name: "Search tactics" }), {
            target: { value: "dfw" },
        });

        // Then
        expect(screen.queryByText("ID PL-7")).toBeNull();
        expect(screen.getByText("ID PL-8")).toBeTruthy();
    });

    it("should fall back to the plan sheet row when the plan has no line id column test", () => {
        // Given: the header sits on sheet row 2, the tactic on row 3, no ID or Geo column
        const mapping: MappingEntry[] = [{ tacticNum: 1, tacticName: "Google SEM" }];
        const planRows = [[], ["Media", "Rate Type", "Units", "Unit Price"], ["Google SEM", "CPC", "1200", "2.5"]];

        // When
        renderModal(mapping, matchResult(mapping, { "650121": "Google Search" }), planRows);

        // Then
        const tags = Array.from(document.querySelectorAll(".match-tactic-tags__item")).map((el) => el.textContent);
        expect(tags).toEqual(["Row 3"]);
    });

    it("should assign the dropped id to the filtered tactic, not to the row at its screen position test", () => {
        // Given: only the third tactic is visible after searching
        const mapping: MappingEntry[] = [
            { tacticNum: 1, tacticName: "Programmatic Display" },
            { tacticNum: 2, tacticName: "Native Display" },
            { tacticNum: 3, tacticName: "DOOH" },
        ];
        renderModal(mapping, matchResult(mapping, { "650115": "Display", "650128": "DOOH" }));
        fireEvent.change(screen.getByRole("searchbox", { name: "Search tactics" }), {
            target: { value: "dooh" },
        });
        const row = screen.getByText("DOOH", { selector: ".match-tactic-name" }).closest(".match-tactic-row");
        expect(row).not.toBeNull();

        // When
        fireEvent.drop(row as Element, { dataTransfer: { getData: () => "650128" } });

        // Then
        expect(screen.getByTestId("mapping").textContent).toBe(
            "Programmatic Display=|Native Display=|DOOH=650128"
        );
        expect(within(row as HTMLElement).getByText("650128")).toBeTruthy();
    });
});
