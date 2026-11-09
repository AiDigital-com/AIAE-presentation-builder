import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { SHEET_FORMATS } from "../lib/sheetFormats";
import { SheetFormatHint } from "./SheetFormatHint";

describe("SheetFormatHint", () => {
    it("should list the Elevate rules and link to the example sheet", () => {
        // Given: the Elevate data format
        const spec = SHEET_FORMATS.elevate;

        // When
        render(<SheetFormatHint format="elevate" />);

        // Then: every rule is listed and the example opens in a new tab
        expect(screen.getByText(spec.summary)).toBeTruthy();
        expect(screen.getAllByRole("listitem", { hidden: true })).toHaveLength(spec.rules.length);
        expect(screen.getByText(/tab named "Basic"/)).toBeTruthy();
        const link = screen.getByRole("link", { name: /Open example sheet/, hidden: true });
        expect(link.getAttribute("href")).toBe(spec.exampleUrl);
        expect(link.getAttribute("target")).toBe("_blank");
    });

    it("should start collapsed so the form stays short", () => {
        // Given / When
        const { container } = render(<SheetFormatHint format="mediaPlan" />);

        // Then
        expect(container.querySelector("details")?.open).toBe(false);
    });
});
