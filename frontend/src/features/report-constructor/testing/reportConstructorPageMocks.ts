import { vi } from "vitest";

/** Supplies the result returned by the mocked {@code useReportResume}. */
export const resumeQuery = vi.fn();

/** Stands in for the sheet-summary read in ReportConstructorPage tests. */
export const readSheetSummary = vi.fn();

/** Stands in for the single-tab sheet read in ReportConstructorPage tests. */
export const readSheetTab = vi.fn();

/** Stands in for the adopt-sheet mutation in ReportConstructorPage tests. */
export const adopt = vi.fn();

/** Stands in for react-router's navigate function in ReportConstructorPage tests. */
export const navigate = vi.fn();
