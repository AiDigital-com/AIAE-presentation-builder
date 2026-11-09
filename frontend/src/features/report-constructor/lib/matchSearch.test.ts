import { describe, expect, it } from "vitest";
import { matchesSearch } from "./matchSearch";

const NAMING = "GCVB_Grapevine Convention & Visitors Bureau_99_Main Street Fest 2026_CTV_Prospecting_CPM_-_650124_SYNTH-TEST";

describe("matchesSearch", () => {
    it("should match everything when the query is blank test", () => {
        // Given
        const query = "   ";

        // When
        const result = matchesSearch(query, ["CTV Precision Reach"]);

        // Then
        expect(result).toBe(true);
    });

    it("should match a line item by a fragment of its id test", () => {
        // Given
        const query = "50124";

        // When
        const result = matchesSearch(query, ["650124", NAMING, "CTV", ""]);

        // Then
        expect(result).toBe(true);
    });

    it("should ignore case test", () => {
        // Given
        const query = "PRECISION reach";

        // When
        const result = matchesSearch(query, ["CTV Precision Reach"]);

        // Then
        expect(result).toBe(true);
    });

    it("should require every word, each from any field test", () => {
        // Given: "ctv" is in the channel, "synth" only in the naming
        const fields = ["650124", NAMING, "CTV"];

        // When
        const both = matchesSearch("ctv synth", fields);
        const oneMissing = matchesSearch("ctv meta", fields);

        // Then
        expect(both).toBe(true);
        expect(oneMissing).toBe(false);
    });

    it("should skip missing fields without matching the words null or undefined test", () => {
        // Given
        const fields = ["Meta (CPM)", undefined, null];

        // When
        const result = matchesSearch("undefined", fields);

        // Then
        expect(result).toBe(false);
    });
});
