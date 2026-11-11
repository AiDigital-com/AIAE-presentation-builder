import { createContext } from "react";

import type { WizardContextValue } from "../model/wizardContext";

/** React context carrying the Report Constructor wizard state; provided by {@code WizardProvider}. */
export const WizardContext = createContext<WizardContextValue | null>(null);
