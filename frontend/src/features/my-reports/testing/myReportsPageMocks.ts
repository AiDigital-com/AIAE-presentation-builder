import { vi } from "vitest";

/** Stands in for react-router's navigate function in MyReportsPage tests. */
export const navigate = vi.fn();

/** Stands in for the dismiss-report mutation in MyReportsPage tests. */
export const dismiss = vi.fn();

/** Supplies the reports query result returned by the mocked {@code useMyReports}. */
export const reportsQuery = vi.fn();
