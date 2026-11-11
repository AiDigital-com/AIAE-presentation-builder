import type { ReportType, TemplateVariant } from "@/shared/api/types";

export interface TypeCard {
    /** Backend report type, or null when the card is not yet wired up. */
    id: ReportType | null;
    name: string;
    tag: string;
    desc: string;
}

export interface TemplateTab {
    id: TemplateVariant;
    label: string;
    /** Short marker next to the label, e.g. "Beta". */
    badge?: string;
    cards: TypeCard[];
}

export interface Props {
    onContinue(): void;
}
