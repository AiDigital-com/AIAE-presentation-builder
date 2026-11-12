import type { BreakdownState } from "../model/stepBreakdowns";
import type { InputErrors } from "../model/stepDataInputs";

export const DEFAULT_BREAKDOWNS: BreakdownState = { tp: false, ca: false, geo: false, aud: false, dev: false };

export const NO_ERRORS: InputErrors = {
    brief: false,
    marketVolume: false,
    sheet: false,
    adj: false,
    dates: false,
};

export const JOB_TOTAL = 7;

// Step numbers the SLIDES_FROM_SHEET job reports, in order (ReportGenerationServiceImpl
// #runSlidesFromSheet emits 1, 3, 6, 7 — not a dense 1..7 run). Each checkpoint maps to one
// displayed stage in StepGenerate, so the visual stages stay in sync with the real work.
export const GEN_STEP_CHECKPOINTS = [1, 3, 6, 7];

export const usd = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD", maximumFractionDigits: 0 });

export const grouped = new Intl.NumberFormat("en-US");
