import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import App from "./App";

vi.mock("@clerk/clerk-react", () => ({
    UserButton: () => <div data-testid="user-button-stub" />,
    useUser: () => ({ user: null, isLoaded: true }),
    useClerk: () => ({ signOut: vi.fn() }),
}));

/**
 * Frontend smoke test — renders App with QueryClient (AppShell uses Clerk
 * UserButton; mocked here because this test mounts App without AppRoot).
 * The /auth/me network call is left to fail (retry: false on the query),
 * which exercises the loading → error transition without needing a fetch
 * mock for a "does the app even render" gate.
 *
 * MemoryRouter stands in for the BrowserRouter that AppRoot provides in the
 * real tree: the page reads the `resume` query parameter and navigates after
 * adopting a sheet, so useNavigate/useSearchParams throw outside a router.
 * Memory history keeps the smoke test off jsdom's global location.
 */
describe("App", () => {
    it("should render the scaffold heading test", () => {
        // Given:
        const queryClient = new QueryClient({
            defaultOptions: { queries: { retry: false } },
        });

        // When:
        render(
            <QueryClientProvider client={queryClient}>
                <MemoryRouter>
                    <App />
                </MemoryRouter>
            </QueryClientProvider>
        );

        // Then:
        expect(screen.getByRole("heading", { level: 1 })).toBeTruthy();
    });
});
