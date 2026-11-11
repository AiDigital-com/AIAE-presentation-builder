import type { Props } from "./model/loadingBlock";
import "./ui-states.css";

/** Centered loading indicator for async/query states. */
export function LoadingBlock({ label = "Loading…" }: Props) {
    return (
        <p className="ui-state ui-state--loading" role="status" aria-live="polite">
            {label}
        </p>
    );
}
