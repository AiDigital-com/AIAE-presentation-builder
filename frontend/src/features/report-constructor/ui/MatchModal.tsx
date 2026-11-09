import { useEffect, useMemo, useState } from "react";
import type { LineItemMatchResult, MappingEntry } from "@/shared/api/types";
import { useWizard } from "@/shared/wizard/WizardContext";
import { extractTacticBudgets, namingTail, type TacticBudget } from "../lib/mediaPlanBudget";
import { matchesSearch } from "../lib/matchSearch";
import { IconCheck, IconInfo, IconLink2, IconRefresh, IconSpinner } from "./icons";

interface Props {
    open: boolean;
    matchData: LineItemMatchResult | null;
    running: boolean;
    onClose(): void;
    onRun(): void;
    onConfirm(): void;
}

function abbreviateNaming(naming: string): string {
    if (!naming) return "";
    const parts = naming.split("_");
    if (parts.length >= 5) return parts.slice(2, 5).join(" · ");
    return naming.substring(0, 55) + (naming.length > 55 ? "…" : "");
}

const usd = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD", maximumFractionDigits: 0 });

function compactUnits(n: number): string {
    if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(n >= 10_000_000 ? 0 : 1).replace(/\.0$/, "")}M`;
    if (n >= 10_000) return `${Math.round(n / 1000)}K`;
    return new Intl.NumberFormat("en-US").format(n);
}

/** One compact line for the left panel, e.g. "$64,000 · 9.1M @ $7 CPM". */
function budgetLine(b: TacticBudget): string {
    const seg: string[] = [];
    if (b.amount > 0) seg.push(usd.format(Math.round(b.amount)));
    if (b.units > 0) {
        const rate = b.rateType ? ` @ $${b.unitPrice} ${b.rateType.toUpperCase()}` : "";
        seg.push(`${compactUnits(b.units)}${rate}`);
    }
    return seg.join(" · ");
}

/**
 * The plan line's own ID (or, when the plan has no ID column, its sheet row) and its Geo, so two
 * lines with the same tactic name can be told apart and found in the plan.
 */
function PlanTags({ plan }: { plan: TacticBudget }) {
    return (
        <div className="match-tactic-tags">
            {plan.planLineId ? (
                <span className="match-tactic-tags__item" title="Line ID from the media plan">
                    ID {plan.planLineId}
                </span>
            ) : (
                <span className="match-tactic-tags__item" title={`Media plan sheet row ${plan.sheetRow}`}>
                    Row {plan.sheetRow}
                </span>
            )}
            {plan.geo && (
                <span className="match-tactic-tags__item" title={`Geo from the media plan: ${plan.geo}`}>
                    {plan.geo}
                </span>
            )}
        </div>
    );
}

interface SearchProps {
    label: string;
    placeholder: string;
    value: string;
    onChange(value: string): void;
}

/** Search box above one of the matching lists; Escape or ✕ clears it. */
function MatchSearch({ label, placeholder, value, onChange }: SearchProps) {
    return (
        <div className="match-search">
            <input
                className="match-search__input"
                type="search"
                aria-label={label}
                placeholder={placeholder}
                value={value}
                onChange={(e) => onChange(e.target.value)}
                onKeyDown={(e) => {
                    if (e.key === "Escape" && value) {
                        e.stopPropagation();
                        onChange("");
                    }
                }}
            />
            {value && (
                <button
                    type="button"
                    className="match-search__clear"
                    aria-label={`Clear ${label.toLowerCase()}`}
                    onClick={() => onChange("")}
                >
                    ×
                </button>
            )}
        </div>
    );
}

export function MatchModal({ open, matchData, running, onClose, onRun, onConfirm }: Props) {
    const { mapping, setMapping, mediaPlan, excludedTactics, toggleTacticExcluded } = useWizard();
    const [dragOver, setDragOver] = useState<number | null>(null);
    const [tacticQuery, setTacticQuery] = useState("");
    const [idQuery, setIdQuery] = useState("");

    // Budget/volume per tactic, aligned to the mapping order so duplicated
    // tactic labels ("Programmatic Display" twice) each get their own row.
    const budgets = useMemo(
        () => extractTacticBudgets(mediaPlan?.sheetRows ?? null, (mapping ?? []).map((m) => m.tacticName)),
        [mediaPlan, mapping]
    );

    useEffect(() => {
        document.body.style.overflow = open ? "hidden" : "";
        return () => {
            document.body.style.overflow = "";
        };
    }, [open]);

    const rows: MappingEntry[] = mapping ?? [];
    const idNamings = matchData?.idNamings ?? {};
    const allIds = matchData?.uniqueIds ?? [];
    // Dropped rows release their ID back to the pool: the report is not about that line item any more,
    // so it must be free to be assigned to a tactic that is being reported.
    const isExcluded = (row: MappingEntry) => excludedTactics.includes(row.tacticNum);
    const kept = rows.filter((m) => !isExcluded(m));
    const usedIds = new Set(kept.map((m) => m.lineItemId).filter(Boolean) as string[]);
    const pool = allIds.filter((id) => !usedIds.has(id));
    const matched = kept.filter((m) => m.lineItemId).length;
    const unmatched = kept.filter((m) => !m.lineItemId);
    const excludedCount = rows.length - kept.length;
    // Position in the report — dropped rows are skipped, so the numbers stay 1..N with no gaps, exactly
    // as the deck and the sheet will number them.
    const reportNums = new Map<number, number>();
    kept.forEach((m, i) => reportNums.set(m.tacticNum, i + 1));

    // Search filters only what is shown; indexes stay those of `rows`, so drag/drop and the
    // assign/remove handlers keep addressing the right mapping entry while a filter is active.
    const tacticVisible = rows.map((row, idx) => {
        const id = isExcluded(row) ? undefined : row.lineItemId;
        const plan = budgets[idx];
        return matchesSearch(tacticQuery, [
            row.tacticName,
            plan ? budgetLine(plan) : "",
            plan?.geo,
            plan?.planLineId,
            plan ? `row ${plan.sheetRow}` : "",
            id,
            id ? idNamings[id]?.naming : undefined,
        ]);
    });
    const visibleTacticCount = tacticVisible.filter(Boolean).length;
    const visiblePool = pool.filter((id) => {
        const info = idNamings[id];
        return matchesSearch(idQuery, [id, info?.naming, info?.channel, info?.tactic]);
    });

    function assign(idx: number, id: string) {
        setMapping(
            rows.map((m, i) => {
                if (i === idx) {
                    return { ...m, lineItemId: id, namingSample: idNamings[id]?.naming, autoMatched: false };
                }
                if (m.lineItemId === id) {
                    return { ...m, lineItemId: undefined, namingSample: undefined, autoMatched: false };
                }
                return m;
            })
        );
    }
    function remove(idx: number) {
        setMapping(
            rows.map((m, i) =>
                i === idx ? { ...m, lineItemId: undefined, namingSample: undefined, autoMatched: false } : m
            )
        );
    }

    return (
        <div
            className={`match-overlay${open ? " visible" : ""}`}
            onClick={(e) => {
                if (e.target === e.currentTarget) onClose();
            }}
        >
            <div className="match-modal">
                <div className="match-modal-head">
                    <div className="match-modal-icon">
                        <IconLink2 />
                    </div>
                    <div>
                        <div className="match-modal-title">Line Item Matching</div>
                        <div className="match-modal-desc">
                            Left — tactics from the media plan. Right — all Line Item IDs found in BQ. Drag the
                            right ID onto a tactic, or click "Re-run" to let the system try to match
                            automatically. Use ✕ on a plan line the report is not about: it is left out
                            entirely — no slides, no numbers, no narrative.
                        </div>
                    </div>
                </div>

                <div className="match-modal-body">
                    {matchData && matchData.warnings.length > 0 && (
                        <div className="match-warning visible">
                            <IconInfo size={14} />
                            <span>{matchData.warnings.join(" · ")}</span>
                        </div>
                    )}

                    {!matchData ? (
                        <div className="match-empty">
                            {running
                                ? "Analyzing data…"
                                : "Connect the Media Plan and BQ export, then click \"Run Matching\""}
                        </div>
                    ) : (
                        <div className="match-layout">
                            <div className="match-tactics-panel">
                                <div className="match-panel-label">
                                    <span>Tactics from media plan</span>
                                    <span>
                                        {matched}/{kept.length}
                                        {excludedCount > 0 && ` · ${excludedCount} excluded`}
                                        {tacticQuery.trim() && ` · ${visibleTacticCount} shown`}
                                    </span>
                                </div>
                                {rows.length > 0 && (
                                    <MatchSearch
                                        label="Search tactics"
                                        placeholder="Search tactics, budget or matched ID…"
                                        value={tacticQuery}
                                        onChange={setTacticQuery}
                                    />
                                )}
                                <div className="match-tactics-list">
                                {rows.length === 0 && (
                                    <div className="match-empty">No tactics found under the "Media" cell</div>
                                )}
                                {rows.length > 0 && visibleTacticCount === 0 && (
                                    <div className="match-empty">No tactics match “{tacticQuery.trim()}”</div>
                                )}
                                {rows.map((row, idx) => {
                                    if (!tacticVisible[idx]) return null;
                                    const excluded = isExcluded(row);
                                    const hasId = !!row.lineItemId && !excluded;
                                    const naming = hasId ? idNamings[row.lineItemId as string]?.naming ?? "" : "";
                                    return (
                                        <div
                                            key={row.tacticNum}
                                            className={`match-tactic-row${hasId ? " has-id" : ""}${
                                                excluded ? " excluded" : ""
                                            }${dragOver === idx ? " drag-over" : ""}`}
                                            onDragOver={(e) => {
                                                if (excluded) return;
                                                e.preventDefault();
                                                setDragOver(idx);
                                            }}
                                            onDragLeave={() => setDragOver((d) => (d === idx ? null : d))}
                                            onDrop={(e) => {
                                                if (excluded) return;
                                                e.preventDefault();
                                                setDragOver(null);
                                                const id = e.dataTransfer.getData("text/plain");
                                                if (id) assign(idx, id);
                                            }}
                                        >
                                            <span className="match-tactic-num">
                                                {excluded ? "—" : reportNums.get(row.tacticNum)}
                                            </span>
                                            <div style={{ flex: 1, minWidth: 0 }}>
                                                <div className="match-tactic-name">{row.tacticName}</div>
                                                {budgets[idx] && <PlanTags plan={budgets[idx] as TacticBudget} />}
                                                {budgets[idx] && budgetLine(budgets[idx] as TacticBudget) && (
                                                    <div
                                                        className="match-tactic-meta"
                                                        title="Planned budget · volume from the media plan"
                                                    >
                                                        {budgetLine(budgets[idx] as TacticBudget)}
                                                    </div>
                                                )}
                                                {hasId && naming && (
                                                    <div
                                                        title={naming}
                                                        style={{
                                                            fontSize: "10px",
                                                            color: "var(--text-muted)",
                                                            fontFamily: "'DM Mono', monospace",
                                                            marginTop: "2px",
                                                            overflow: "hidden",
                                                            textOverflow: "ellipsis",
                                                            whiteSpace: "nowrap",
                                                        }}
                                                    >
                                                        {abbreviateNaming(naming)}
                                                    </div>
                                                )}
                                            </div>
                                            <div className="match-tactic-badge">
                                                <span className={`match-id-pill${hasId ? "" : " empty"}`}>
                                                    {excluded
                                                        ? "not reported"
                                                        : hasId
                                                          ? row.lineItemId
                                                          : "drop here"}
                                                </span>
                                                {hasId && (
                                                    <button
                                                        className="match-remove-btn"
                                                        title="Unlink this ID"
                                                        onClick={() => remove(idx)}
                                                    >
                                                        ×
                                                    </button>
                                                )}
                                                {excluded ? (
                                                    <button
                                                        className="match-restore-btn"
                                                        title="Put this line back into the report"
                                                        onClick={() => toggleTacticExcluded(row.tacticNum)}
                                                    >
                                                        Restore
                                                    </button>
                                                ) : (
                                                    <button
                                                        className="match-exclude-btn"
                                                        title="Exclude this line from the report — no slides, no numbers, no narrative"
                                                        onClick={() => toggleTacticExcluded(row.tacticNum)}
                                                    >
                                                        ✕
                                                    </button>
                                                )}
                                            </div>
                                        </div>
                                    );
                                })}
                                </div>
                            </div>

                            <div className="match-ids-panel">
                                <div className="match-panel-label">
                                    <span>Line Item IDs from BQ</span>
                                    <span>
                                        {allIds.length} unique
                                        {idQuery.trim() && ` · ${visiblePool.length} shown`}
                                    </span>
                                </div>
                                {pool.length > 0 && (
                                    <MatchSearch
                                        label="Search line items"
                                        placeholder="Search ID, naming, channel…"
                                        value={idQuery}
                                        onChange={setIdQuery}
                                    />
                                )}
                                <div className="match-ids-pool">
                                    <div className="match-ids-pool-inner">
                                        {allIds.length === 0 ? (
                                            <div className="match-drag-hint">No IDs found in the BQ export</div>
                                        ) : pool.length === 0 ? (
                                            <div className="match-drag-hint" style={{ padding: "24px" }}>
                                                All IDs assigned ✓
                                            </div>
                                        ) : visiblePool.length === 0 ? (
                                            <div className="match-drag-hint" style={{ padding: "24px" }}>
                                                No line items match “{idQuery.trim()}”
                                            </div>
                                        ) : (
                                            <>
                                                {visiblePool.map((id) => {
                                                    const info = idNamings[id] ?? { naming: "", channel: "", tactic: "" };
                                                    const tail = namingTail(info.naming, id);
                                                    return (
                                                        <div
                                                            key={id}
                                                            className="match-id-card"
                                                            draggable
                                                            title={info.naming}
                                                            onDragStart={(e) =>
                                                                e.dataTransfer.setData("text/plain", id)
                                                            }
                                                        >
                                                            <span className="match-id-card-num">{id}</span>
                                                            {tail && (
                                                                <span className="match-id-card-tail" title={info.naming}>
                                                                    {tail}
                                                                </span>
                                                            )}
                                                            {info.channel && (
                                                                <div style={{ fontSize: "11px", color: "var(--text-muted)" }}>
                                                                    <span style={{ opacity: 0.6 }}>ch:</span> {info.channel}
                                                                </div>
                                                            )}
                                                            {info.tactic && (
                                                                <div style={{ fontSize: "11px", color: "var(--text-muted)" }}>
                                                                    <span style={{ opacity: 0.6 }}>tactic:</span>{" "}
                                                                    {info.tactic}
                                                                </div>
                                                            )}
                                                        </div>
                                                    );
                                                })}
                                                <div className="match-drag-hint">← Drag onto a tactic</div>
                                            </>
                                        )}
                                    </div>
                                </div>
                            </div>
                        </div>
                    )}
                </div>

                <div className="match-modal-foot">
                    <div className="match-modal-foot-left">
                        <button className="btn-match-run" disabled={running} onClick={onRun}>
                            {running ? <IconSpinner /> : <IconRefresh />}
                            {matchData ? "Re-run" : "Run Matching"}
                        </button>
                        <span className="match-stats">
                            {matchData
                                ? `${matched}/${kept.length} matched · ${allIds.length} unique IDs in BQ` +
                                  (excludedCount > 0 ? ` · ${excludedCount} line(s) excluded` : "")
                                : ""}
                        </span>
                    </div>
                    <div style={{ display: "flex", flexDirection: "column", alignItems: "flex-end", gap: "8px" }}>
                        {rows.length > 0 && kept.length === 0 && (
                            <div
                                style={{
                                    fontSize: "11px",
                                    color: "var(--red)",
                                    textAlign: "right",
                                    maxWidth: "300px",
                                    lineHeight: 1.4,
                                }}
                            >
                                Every plan line is excluded — restore at least one to build a report.
                            </div>
                        )}
                        {unmatched.length > 0 && (
                            <div
                                style={{
                                    fontSize: "11px",
                                    color: "var(--orange)",
                                    textAlign: "right",
                                    maxWidth: "300px",
                                    lineHeight: 1.4,
                                }}
                            >
                                ⚠ {unmatched.length} tactic(s) without an ID — their data will show as 0:{" "}
                                {unmatched.map((m) => m.tacticName).join(", ")}
                            </div>
                        )}
                        <div style={{ display: "flex", gap: "10px", alignItems: "center" }}>
                            <button className="btn-match-cancel" onClick={onClose}>
                                Cancel
                            </button>
                            <button
                                className="btn-match-confirm"
                                disabled={!matchData || kept.length === 0}
                                onClick={onConfirm}
                            >
                                <IconCheck />
                                Confirm Mapping
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}
