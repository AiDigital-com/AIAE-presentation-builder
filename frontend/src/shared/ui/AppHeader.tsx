import { UserButton } from "@clerk/clerk-react";

import { useVersionQuery } from "@/shared/api/useVersionQuery";
import type { Props } from "./model/appHeader";
import "./app-shell.css";

/** Top app header — Elevate layout (no left sidebar). */
export function AppHeader({ appName }: Props) {
    const { data: version } = useVersionQuery();

    return (
        <header className="app-header">
            <span className="app-header__brand">{appName}</span>
            <div className="app-header__actions">
                {version && (
                    <span
                        className="app-header__version"
                        title={version.commitTime ? `Deployed commit ${version.commitId} at ${version.commitTime}` : `Deployed commit ${version.commitId}`}
                    >
                        build {version.commitId}
                    </span>
                )}
                <UserButton afterSignOutUrl="/login" />
            </div>
        </header>
    );
}
