/**
 * Free-text filter for the Line Item Matching lists.
 *
 * The query is split on whitespace and every word must appear (case-insensitive) somewhere in the
 * given fields, so "ctv 650124" narrows to the CTV line item 650124 regardless of which field holds
 * which word. An empty or blank query matches everything.
 */
export function matchesSearch(query: string, fields: ReadonlyArray<string | null | undefined>): boolean {
    const terms = query.trim().toLowerCase().split(/\s+/).filter(Boolean);
    if (terms.length === 0) return true;
    const haystack = fields
        .filter((f): f is string => !!f)
        .join(" ")
        .toLowerCase();
    return terms.every((t) => haystack.includes(t));
}
