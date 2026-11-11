import type { PaidChannelSpec } from "../model/stepDataInputs";
import { PAID_SEARCH_EXPORT_TABS, PAID_SOCIAL_EXPORT_TABS } from "@/shared/api/sheets";

export const PAID_CHANNELS: PaidChannelSpec[] = [
    { id: "search", name: "Paid Search", label: "Paid Search (Google Ads) export — Google Sheet link", tabs: PAID_SEARCH_EXPORT_TABS },
    { id: "social", name: "Paid Social", label: "Paid Social (Meta) export — Google Sheet link", tabs: PAID_SOCIAL_EXPORT_TABS },
];
