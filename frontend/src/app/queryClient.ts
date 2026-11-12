import { QueryClient } from "@tanstack/react-query";

/** The app-wide TanStack Query client: 30 s staleness, no refetch on window focus. */
export const queryClient = new QueryClient({
    defaultOptions: {
        queries: { staleTime: 30_000, refetchOnWindowFocus: false },
    },
});
