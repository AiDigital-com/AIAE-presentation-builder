import type { LineItemMatchResult } from "@/shared/api/types";

export interface Props {
    open: boolean;
    matchData: LineItemMatchResult | null;
    running: boolean;
    onClose(): void;
    onRun(): void;
    onConfirm(): void;
}

export interface SearchProps {
    label: string;
    placeholder: string;
    value: string;
    onChange(value: string): void;
}
