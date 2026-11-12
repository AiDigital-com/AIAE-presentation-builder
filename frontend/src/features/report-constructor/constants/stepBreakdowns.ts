import type { BreakdownId } from "../model/stepBreakdowns";

export const BREAKDOWNS = [
    { id: "tp", label: "Top Publishers" },
    { id: "ca", label: "Creative Analysis" },
    { id: "geo", label: "Geo Performance" },
    { id: "aud", label: "Audience Analysis" },
    { id: "dev", label: "Device Breakdown" },
] as const;

/**
 * Walled-garden tactics: the ad platform reports no publisher and no device split, so only creative,
 * geo and audience analysis can ever be filled in. Mirrors the channel map in the backend
 * TacticCatalog for Meta, TikTok, Google Search and Performance Max.
 */
export const CREATIVE_GEO_AUDIENCE_ONLY_TACTICS = new Set([
    "meta (cpm)",
    "meta (cpc)",
    "facebook specific",
    "meta lead forms",
    "meta boosted posts",
    "instagram specific",
    "tiktok (cpm)",
    "tiktok (cpc)",
    "tiktok spark ads (cpm)",
    "tiktok spark ads (cpc)",
    "tiktok search ads",
    "google sem",
    "demand gen",
    "performance max",
]);

/** Channels matching the tactics above, for mappings that carry an explicit channel. */
export const CREATIVE_GEO_AUDIENCE_ONLY_CHANNELS = new Set(["meta", "tiktok", "google search", "performance max"]);

export const CREATIVE_GEO_AUDIENCE_IDS: readonly BreakdownId[] = ["ca", "geo", "aud"];

/** Max tactics the report deck template supports; extras are trimmed to this at generation time. */
export const MAX_TACTICS = 28;
