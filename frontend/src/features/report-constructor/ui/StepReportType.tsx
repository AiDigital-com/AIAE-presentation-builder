
import { useWizard } from "@/shared/wizard/WizardContext";
import { IconArrowRight, IconCheck } from "./icons";
import type { Props } from "../model/stepReportType";
import { TABS } from "../constants/stepReportType";

/** Screen 1 — pick the template family (tab) and the deliverable (report type). */
export function StepReportType({ onContinue }: Props) {
    const { reportType, setReportType, templateVariant, setTemplateVariant } = useWizard();
    const activeTab = TABS.find((t) => t.id === templateVariant) ?? TABS[0];

    return (
        <div className="rc-content">
            <div className="rc-hero">
                <span className="rc-hero__eyebrow">
                    <span className="rc-hero__eyebrow-dot" />
                    Campaign Report Generator
                </span>
                <h1 className="rc-hero__title">Start a new report</h1>
                <p className="rc-hero__sub">
                    Pick a deliverable. We read your Sheets, apply the brief, and build the report for you.
                </p>
            </div>

            <div className="rc-section-head">
                <div className="rc-section-head__num">01</div>
                <div>
                    <h2 className="rc-section-head__title">Choose report type</h2>
                    <p className="rc-section-head__sub">This sets the template, sections and output format.</p>
                </div>
            </div>

            <div className="rc-tabs" role="tablist" aria-label="Template">
                {TABS.map((tab) => {
                    const active = tab.id === activeTab.id;
                    return (
                        <button
                            type="button"
                            key={tab.id}
                            role="tab"
                            aria-selected={active}
                            className={`rc-tabs__tab${active ? " rc-tabs__tab--active" : ""}`}
                            onClick={() => setTemplateVariant(tab.id)}
                        >
                            {tab.label}
                            {tab.badge && <span className="rc-tabs__badge">{tab.badge}</span>}
                        </button>
                    );
                })}
            </div>

            <div className="rc-typegrid" role="tabpanel" aria-label={activeTab.label}>
                {activeTab.cards.map((card) => {
                    const disabled = card.id === null;
                    const selected = !disabled && card.id === reportType;
                    return (
                        <button
                            type="button"
                            key={card.name}
                            className={`rc-typecard${selected ? " rc-typecard--selected" : ""}${
                                disabled ? " rc-typecard--disabled" : ""
                            }`}
                            disabled={disabled}
                            aria-pressed={selected}
                            onClick={() => card.id && setReportType(card.id)}
                        >
                            <div className="rc-typecard__top">
                                <span className="rc-typecard__name">{card.name}</span>
                                {disabled ? (
                                    <span className="rc-typecard__soon">Soon</span>
                                ) : (
                                    <span className="rc-typecard__tick">{selected && <IconCheck size={12} />}</span>
                                )}
                            </div>
                            <span className="rc-typecard__tag">{card.tag}</span>
                            <p className="rc-typecard__desc">{card.desc}</p>
                        </button>
                    );
                })}
            </div>

            <div className="rc-actions rc-actions--end">
                <button type="button" className="rc-btn rc-btn--primary" onClick={onContinue}>
                    Continue
                    <IconArrowRight size={16} />
                </button>
            </div>
        </div>
    );
}
