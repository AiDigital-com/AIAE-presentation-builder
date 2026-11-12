import { SHEET_FORMATS } from "../lib/sheetFormats";
import type { Props } from "../model/sheetFormatHint";

/**
 * Collapsible "expected format" note under a sheet-link field: the rules the workbook has to follow
 * and a link to an example workbook that already follows them. Collapsed by default so the form stays
 * short for users who know the format.
 */
export function SheetFormatHint({ format }: Props) {
    const spec = SHEET_FORMATS[format];
    return (
        <details className="rc-format">
            <summary className="rc-format__summary">{spec.summary}</summary>
            <ul className="rc-format__rules">
                {spec.rules.map((rule) => (
                    <li key={rule} className="rc-format__rule">
                        {rule}
                    </li>
                ))}
            </ul>
            <a className="rc-format__example" href={spec.exampleUrl} target="_blank" rel="noopener noreferrer">
                Open example sheet ↗
            </a>
        </details>
    );
}
