import type { ReactNode } from "react";

export interface Props {
    title: string;
    subtitle?: string;
    actions?: ReactNode;
}
