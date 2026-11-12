import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { DateRangePicker } from "./DateRangePicker";

describe("DateRangePicker", () => {
    it("should flag a date later than today and refuse to apply it", () => {
        // Given: the picker is open on a valid window
        const onChange = vi.fn();
        render(<DateRangePicker value={{ from: "2026-01-01", to: "2026-01-31" }} onChange={onChange} />);
        fireEvent.click(screen.getByRole("button", { expanded: false }));

        // When: a date in the future is typed into "To"
        const to = screen.getByLabelText("To");
        fireEvent.change(to, { target: { value: "2999-01-01" } });

        // Then: the field is marked invalid, linked to an announced error, and Apply is disabled
        const alert = screen.getByRole("alert");
        expect(alert.textContent).toBe("Dates can't be later than today.");
        expect(to.getAttribute("aria-invalid")).toBe("true");
        expect(to.getAttribute("aria-describedby")).toBe(alert.id);
        expect(screen.getByLabelText("From").getAttribute("aria-invalid")).toBeNull();
        expect((screen.getByRole("button", { name: "Apply" }) as HTMLButtonElement).disabled).toBe(true);
        expect(onChange).not.toHaveBeenCalled();
    });

    it("should apply a valid custom window", () => {
        // Given: the picker is open
        const onChange = vi.fn();
        render(<DateRangePicker value={{ from: "2026-01-01", to: "2026-01-31" }} onChange={onChange} />);
        fireEvent.click(screen.getByRole("button", { expanded: false }));

        // When: an earlier "From" is chosen and applied
        fireEvent.change(screen.getByLabelText("From"), { target: { value: "2025-12-01" } });
        fireEvent.click(screen.getByRole("button", { name: "Apply" }));

        // Then: no error is shown and the new window is applied
        expect(screen.queryByRole("alert")).toBeNull();
        expect(onChange).toHaveBeenCalledWith({ from: "2025-12-01", to: "2026-01-31" });
    });
});
