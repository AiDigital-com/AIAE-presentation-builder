import type { TemplateTab } from "../model/stepReportType";

// Standard keeps today's deliverables: EOC and EOM are active (backend supports both); Agenda & Excel
// don't exist server-side yet and are shown disabled with a "Soon" marker. MPO is the new deck design,
// offered for both slide reports.
export const TABS: TemplateTab[] = [
    {
        id: "STANDARD",
        label: "Standard templates",
        cards: [
            { id: null, name: "Agenda", tag: "Document", desc: "Kickoff agenda — objectives, timeline, owners and next steps." },
            { id: null, name: "Excel", tag: "Spreadsheet", desc: "Formatted performance workbook with raw pivots per tactic." },
            { id: "EOM", name: "EOM", tag: "Slides", desc: "End-of-month performance review across every active tactic." },
            { id: "EOC", name: "EOC", tag: "Slides", desc: "End-of-campaign wrap — full results, insights and learnings." },
        ],
    },
    {
        id: "MPO",
        label: "MPO templates",
        badge: "Beta",
        cards: [
            {
                id: "EOM",
                name: "EOM",
                tag: "Slides · MPO",
                desc: "Monthly review on the MPO design — pacing dashboard, performance vs. plan and channel-by-channel breakdowns.",
            },
            {
                id: "EOC",
                name: "EOC",
                tag: "Slides · MPO",
                desc: "End-of-campaign wrap on the MPO design — strategic framework, funnel story and per-channel results.",
            },
        ],
    },
];
