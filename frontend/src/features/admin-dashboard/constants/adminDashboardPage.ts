import type { Tab } from "../model/adminDashboardPage";
import type { ReportSortKey } from "../api/useAllReports";

export const dateFmt = new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric", year: "numeric" });

export const updatedFmt = new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
});

export const TAB_LABELS: Record<Tab, string> = {
    overview: "Overview",
    tokens: "Token consumption",
    reports: "All reports",
    failures: "Failures",
    admins: "Admins",
};

/** Columns the report history offers, in the order they appear above the list. */
export const REPORT_SORTS: { key: ReportSortKey; label: string }[] = [
    { key: "createdAt", label: "Date" },
    { key: "tokens", label: "Tokens" },
    { key: "slides", label: "Slides" },
    { key: "owner", label: "Owner" },
    { key: "type", label: "Type" },
    { key: "status", label: "Status" },
];

/** How many rows one page of the history holds. */
export const REPORTS_PAGE_SIZE = 50;
