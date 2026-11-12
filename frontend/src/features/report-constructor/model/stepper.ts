export interface Props {
    /** 0-based index of the active step. */
    active: number;
    /** Highest step the user has reached — steps up to here are navigable. */
    maxReached: number;
    /**
     * Lowest step still navigable. A resumed draft starts at the review step with no source grids
     * in memory, so the earlier steps cannot be re-run and are not offered.
     */
    minReached?: number;
    /** When true, navigation is disabled (e.g. a build/generate job is running). */
    locked?: boolean;
    onNavigate(index: number): void;
}
