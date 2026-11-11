import type { Props } from "./model/emptyState";
import "./ui-states.css";

/** Neutral empty-state copy when a list or query returns no rows. */
export function EmptyState({ message }: Props) {
    return (
        <p className="ui-state ui-state--empty" role="status">
            {message}
        </p>
    );
}
