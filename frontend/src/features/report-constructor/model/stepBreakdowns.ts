import type { BREAKDOWNS } from "../constants/stepBreakdowns";

export type BreakdownId = (typeof BREAKDOWNS)[number]["id"];

export type BreakdownState = Record<BreakdownId, boolean>;

export interface TacticView {
    tacticNum: number;
    name: string;
    channel: string;
    meta: string;
    on: BreakdownState;
}

export interface Props {
    tactics: TacticView[];
    building: boolean;
    /** True once a sheet has already been assembled — enables Continue and gates rebuilds. */
    sheetBuilt: boolean;
    onToggle(tacticNum: number, id: BreakdownId): void;
    onBuild(): void;
    onContinue(): void;
    onBack(): void;
}
