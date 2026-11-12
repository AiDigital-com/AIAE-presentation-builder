import type { ReportType } from "@/shared/api/types";

export type GenStatus = "idle" | "running" | "done";

export interface Props {
    reportType: ReportType;
    status: GenStatus;
    /** Number of stages completed (0..STAGES.length). */
    completed: number;
    resultUrl: string | null;
    /** Non-fatal warnings (a chart that failed to build, a breakdown slide that shipped without its
     * Claude bullets); the report still completes. */
    warnings: string[];
    onGenerate(): void;
    onRunAgain(): void;
    onBack(): void;
}
