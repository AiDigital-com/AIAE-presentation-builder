import type { ReactNode } from "react";

import type { PaidChannel } from "@/shared/wizard/model/wizardContext";

export interface InputErrors {
    brief: boolean;
    marketVolume: boolean;
    sheet: boolean;
    adj: boolean;
    dates: boolean;
}

export interface Props {
    errors: InputErrors;
    mediaPulling: boolean;
    elevatePulling: boolean;
    datesDetecting: boolean;
    matchRunning: boolean;
    onConnectMediaPlan(url: string): void;
    onConnectElevate(url: string): void;
    // Present when auto-detection found no "Proposal"/"Estimates" tab: the workbook's
    // visible tabs, so the user can point us at the tab that holds the media plan.
    mediaTabPicker: { url: string; tabs: string[] } | null;
    onPickMediaTab(url: string, tab: string): void;
    onDismissMediaTabPicker(): void;
    onDisconnectMediaPlan(): void;
    onDisconnectElevate(): void;
    onOpenMatch(): void;
    onOpenPacing(): void;
    onConfirm(): void;
    onBack(): void;
    clearError(key: keyof InputErrors): void;
    /** True while a user-supplied workbook is being read and registered. */
    adopting: boolean;
    onAdoptSheet(url: string): void;
}

export interface ConnectRowProps {
    label: string;
    placeholder: string;
    connected: string | null;
    pulling: boolean;
    onConnect(url: string): void;
    onDisconnect(): void;
    /** Shown under the link field, e.g. the expected sheet format. */
    hint?: ReactNode;
}

export interface MediaTabPickerProps {
    picker: { url: string; tabs: string[] };
    pulling: boolean;
    onPick(url: string, tab: string): void;
    onDismiss(): void;
    /** What the user is choosing a tab for; defaults to the media-plan wording. */
    message?: ReactNode;
}

export interface PaidChannelSpec {
    id: PaidChannel;
    name: string;
    label: string;
    tabs: readonly string[];
}

export interface AdoptSheetCardProps {
    adopting: boolean;
    onAdopt(url: string): void;
}

export interface StatusRowProps {
    label: string;
    done: boolean;
    value: string;
}
