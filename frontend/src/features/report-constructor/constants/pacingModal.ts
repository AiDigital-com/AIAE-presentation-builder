export const RATE_TYPES = ["CPM", "CPC", "CPV"] as const;

/** Unit the rate is charged per — shown under the rate field so the number is unambiguous. */
export const RATE_UNIT: Record<string, string> = {
    CPM: "per 1,000 impressions",
    CPC: "per click",
    CPV: "per view",
};

export const usd = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD", maximumFractionDigits: 0 });

export const grouped = new Intl.NumberFormat("en-US");

export const money = new Intl.NumberFormat("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
