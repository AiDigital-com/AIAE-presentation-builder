import { createContext } from "react";

import type { ToastApi } from "../model/toastContext";

/** React context carrying the toast API; provided by {@code ToastProvider}. */
export const ToastCtx = createContext<ToastApi | null>(null);
