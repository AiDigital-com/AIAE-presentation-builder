// Wizard state shared across all Report Constructor steps. Mirrors the legacy
// single `state` object → React state (no localStorage). Connecting or
// disconnecting either sheet invalidates the line-item match (matchConfirmed
// + mapping reset), matching the legacy gate.
import {
    useCallback,
    useContext,
    useMemo,
    useState,
    type ReactNode,
} from "react";

import type { MappingEntry, ReportType, TemplateVariant } from "../api/types";
import { WizardContext } from "./context/wizardContext";
import type { MediaPlanState, ElevateState, PaidChannel, PaidChannelState, WizardContextValue } from "./model/wizardContext";

export function WizardProvider({ children }: { children: ReactNode }) {
    const [brief, setBriefState] = useState("");
    const [changeLog, setChangeLogState] = useState("");
    const [reportType, setReportTypeState] = useState<ReportType>("EOC");
    const [templateVariant, setTemplateVariantState] = useState<TemplateVariant>("STANDARD");
    const [marketVolume, setMarketVolumeState] = useState("");
    const [estimateDaypartGender, setEstimateDaypartGenderState] = useState(true);
    const [mediaPlan, setMediaPlan] = useState<MediaPlanState | null>(null);
    const [elevate, setElevate] = useState<ElevateState | null>(null);
    const [paidChannels, setPaidChannels] = useState<Record<PaidChannel, PaidChannelState | null>>({
        search: null,
        social: null,
    });
    const [mapping, setMappingState] = useState<MappingEntry[] | null>(null);
    const [excludedTactics, setExcludedTactics] = useState<number[]>([]);
    const [matchConfirmed, setMatchConfirmed] = useState(false);
    const [pacingConfirmed, setPacingConfirmed] = useState(false);
    const [dateStart, setDateStart] = useState("");
    const [dateEnd, setDateEnd] = useState("");
    const [dateConfirmed, setDateConfirmed] = useState(false);
    const [datesEdited, setDatesEdited] = useState(false);

    // Re-matching rebuilds the tactic list, so any pacing entered against the old list is stale too.
    const invalidateMatch = useCallback(() => {
        setMappingState(null);
        setExcludedTactics([]);
        setMatchConfirmed(false);
        setPacingConfirmed(false);
    }, []);

    // The report's tactic list. Kept in plan order: the renumbering to a dense 1..N happens once, at
    // the edge where the generate payload is built, so nothing in the UI has to track two numberings.
    const activeMapping = useMemo(
        () => (mapping ?? []).filter((m) => !excludedTactics.includes(m.tacticNum)),
        [mapping, excludedTactics]
    );

    // Reconnecting/disconnecting the Elevate raw data replaces the "Basic" tab the
    // date window is derived from, so any previously confirmed window is stale.
    const invalidateDates = useCallback(() => {
        setDateStart("");
        setDateEnd("");
        setDateConfirmed(false);
        setDatesEdited(false);
    }, []);

    const value = useMemo<WizardContextValue>(
        () => ({
            brief,
            changeLog,
            reportType,
            templateVariant,
            marketVolume,
            estimateDaypartGender,
            mediaPlan,
            elevate,
            paidChannels,
            mapping,
            excludedTactics,
            activeMapping,
            matchConfirmed,
            pacingConfirmed,
            dateStart,
            dateEnd,
            dateConfirmed,
            datesEdited,
            setBrief: setBriefState,
            setChangeLog: setChangeLogState,
            setReportType: setReportTypeState,
            setTemplateVariant: setTemplateVariantState,
            setMarketVolume: setMarketVolumeState,
            setEstimateDaypartGender: setEstimateDaypartGenderState,
            connectMediaPlan: (v) => {
                setMediaPlan(v);
                invalidateMatch();
            },
            updateMediaPlanTabs: (patch) =>
                setMediaPlan((prev) => (prev ? { ...prev, ...patch } : prev)),
            disconnectMediaPlan: () => {
                setMediaPlan(null);
                invalidateMatch();
            },
            connectElevate: (v) => {
                setElevate(v);
                invalidateMatch();
                invalidateDates();
            },
            disconnectElevate: () => {
                setElevate(null);
                invalidateMatch();
                invalidateDates();
            },
            connectPaidChannel: (channel, v) => setPaidChannels((prev) => ({ ...prev, [channel]: v })),
            disconnectPaidChannel: (channel) => setPaidChannels((prev) => ({ ...prev, [channel]: null })),
            setMapping: (m) => {
                setMappingState(m);
                // A fresh match rebuilds the tactic list, so the old row numbers no longer mean anything.
                setExcludedTactics([]);
                setMatchConfirmed(false);
                setPacingConfirmed(false);
            },
            // Dropping or restoring a row changes which tactics the report covers, so the mapping has
            // to be confirmed again — and the pacing with it, since its rows follow the same list.
            toggleTacticExcluded: (tacticNum) => {
                setExcludedTactics((prev) =>
                    prev.includes(tacticNum) ? prev.filter((n) => n !== tacticNum) : [...prev, tacticNum]
                );
                setMatchConfirmed(false);
                setPacingConfirmed(false);
            },
            confirmMatch: () => setMatchConfirmed(true),
            resetMatch: invalidateMatch,
            setPacing: (m) => {
                setMappingState(m);
                setPacingConfirmed(false);
            },
            confirmPacing: () => setPacingConfirmed(true),
            setDateWindow: (start, end) => {
                setDateStart(start);
                setDateEnd(end);
                setDateConfirmed(false);
                setDatesEdited(true);
            },
            suggestDateWindow: (start, end) => {
                if (datesEdited) return;
                setDateStart(start);
                setDateEnd(end);
                setDateConfirmed(false);
            },
            confirmDates: () => setDateConfirmed(true),
            resetDates: invalidateDates,
        }),
        [
            brief,
            changeLog,
            reportType,
            templateVariant,
            marketVolume,
            estimateDaypartGender,
            mediaPlan,
            elevate,
            paidChannels,
            mapping,
            excludedTactics,
            activeMapping,
            matchConfirmed,
            pacingConfirmed,
            dateStart,
            dateEnd,
            dateConfirmed,
            datesEdited,
            invalidateMatch,
            invalidateDates,
        ]
    );

    return <WizardContext.Provider value={value}>{children}</WizardContext.Provider>;
}

export function useWizard(): WizardContextValue {
    const ctx = useContext(WizardContext);
    if (!ctx) {
        throw new Error("useWizard must be used within a WizardProvider");
    }
    return ctx;
}
