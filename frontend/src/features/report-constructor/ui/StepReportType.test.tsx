import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { useWizard, WizardProvider } from "@/shared/wizard/WizardContext";
import { StepReportType } from "./StepReportType";

/**
 * Prints the wizard choices the generate payload is built from.
 */
function WizardProbe() {
    const { reportType, templateVariant } = useWizard();
    return <output data-testid="probe">{`${templateVariant}/${reportType}`}</output>;
}

/**
 * Renders the step inside a fresh wizard.
 */
function renderStep() {
    render(
        <WizardProvider>
            <StepReportType onContinue={vi.fn()} />
            <WizardProbe />
        </WizardProvider>
    );
}

describe("StepReportType — template tabs", () => {
    it("should open on the standard templates with every existing deliverable test", () => {
        // Given / When
        renderStep();

        // Then: today's cards are all there, and the standard design is the one sent
        expect(screen.getByRole("tab", { name: /standard templates/i }).getAttribute("aria-selected")).toBe("true");
        expect(screen.getByRole("button", { name: /agenda/i })).toBeTruthy();
        expect(screen.getByRole("button", { name: /excel/i })).toBeTruthy();
        expect(screen.getByRole("button", { name: /^eom/i })).toBeTruthy();
        expect(screen.getByRole("button", { name: /^eoc/i })).toBeTruthy();
        expect(screen.getByTestId("probe").textContent).toBe("STANDARD/EOC");
    });

    it("should offer only the slide reports on the MPO tab test", () => {
        // Given
        renderStep();

        // When
        fireEvent.click(screen.getByRole("tab", { name: /mpo templates/i }));

        // Then: the not-yet-built deliverables are not offered on the new design
        expect(screen.getByRole("tab", { name: /mpo templates/i }).getAttribute("aria-selected")).toBe("true");
        expect(screen.queryByRole("button", { name: /agenda/i })).toBeNull();
        expect(screen.queryByRole("button", { name: /excel/i })).toBeNull();
        expect(screen.getAllByText("Slides · MPO")).toHaveLength(2);
    });

    it("should record the MPO design with the chosen report type test", () => {
        // Given
        renderStep();

        // When
        fireEvent.click(screen.getByRole("tab", { name: /mpo templates/i }));
        fireEvent.click(screen.getByRole("button", { name: /^eom/i }));

        // Then
        expect(screen.getByTestId("probe").textContent).toBe("MPO/EOM");
    });

    it("should go back to the standard design when its tab is chosen again test", () => {
        // Given: the user looked at the MPO tab first
        renderStep();
        fireEvent.click(screen.getByRole("tab", { name: /mpo templates/i }));

        // When
        fireEvent.click(screen.getByRole("tab", { name: /standard templates/i }));

        // Then
        expect(screen.getByTestId("probe").textContent).toBe("STANDARD/EOC");
        expect(screen.getByRole("button", { name: /agenda/i })).toBeTruthy();
    });
});
