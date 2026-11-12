import type { ReportType } from "@/shared/api/types";

export interface ReviewRow {
    tactic: string;
    spendPlan: string | null;
    spendFact: string | null;
    /** Main planned unit — impressions for CPM, clicks for CPC, completions for CPV. */
    unitPlan: string | null;
    /** Delivered (fact) main unit, matching whichever unit unitPlan carries. */
    unitFact: string | null;
}

export interface Props {
    reportType: ReportType;
    sheetUrl: string | null;
    rows: ReviewRow[];
    /** True while the summary figures are being re-read from the sheet. */
    refreshing: boolean;
    /**
     * True once the sheet has been read back at least once. Before that, `brief` is only what the
     * saved draft remembered, which says nothing about what the workbook's {{RFP info}} cell holds
     * today — so the "no brief" prompt must wait for the read rather than flash on arrival.
     */
    sheetRead?: boolean;
    /** True when at least one tactic has a breakdown section enabled, so its slides need manual data. */
    breakdownsEnabled: boolean;
    /**
     * True when this step was entered by resuming a saved draft. The earlier steps cannot be
     * re-run — their source grids never existed in this session — so Back is not offered.
     */
    resumed?: boolean;
    /**
     * The campaign brief, shown here only when the workbook did not supply one. A sheet the user
     * brought themselves may have an empty {{RFP info}} cell, and the deck's whole narrative is
     * written from that text — so it is asked for here rather than left to fail at generation.
     */
    brief?: string;
    onBriefChange?(value: string): void;
    onRefresh(): void;
    onConfirm(): void;
    onBack(): void;
}
