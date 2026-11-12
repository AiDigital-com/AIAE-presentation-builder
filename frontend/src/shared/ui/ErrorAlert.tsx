import type { Props } from "./model/errorAlert";
import "./ui-states.css";

/** Accessible error banner for failed queries or form submissions. */
export function ErrorAlert({ message, id }: Props) {
    return (
        <p id={id} className="ui-state ui-state--error" role="alert">
            {message}
        </p>
    );
}
