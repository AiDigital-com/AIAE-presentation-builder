import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import { fileURLToPath, URL } from "node:url";

// Replit-tuned Vite config. Hard rules:
//  - Vite stays on 5173 (backend owns 5000 → externalPort 80).
//  - /api/* proxied to backend; never hardcode backend URLs.
//  - Build → ../backend/application/src/main/resources/static (Spring serves SPA).
//  - allowedHosts must include `.replit.dev`/`.repl.co`/`.kirk.replit.dev`
//    (Vite 5+ blocks unknown Host headers).
// See `templates/generated-project/frontend/canonical-react-frontend-rules.md`
// → "Bootstrap rule" + backend SKILL "Port architecture lock".

export default defineConfig(({ mode }) => {
    const env = loadEnv(mode, process.cwd(), "");
    const backendPort = env.BACKEND_DEV_PORT ?? "5000";
    const backendContextPath = env.VITE_API_CONTEXT_PATH ?? "";   // e.g. "/sales-dashboard"
    // Clerk SSO is the only auth mode. Map Replit-managed CLERK_PUBLISHABLE_KEY
    // into the VITE_-prefixed var the browser sees.
    const clerkPublishableKey =
        env.VITE_CLERK_PUBLISHABLE_KEY ?? env.CLERK_PUBLISHABLE_KEY ?? "";
    const clerkJwtTemplate =
        env.VITE_CLERK_JWT_TEMPLATE ?? "aidigital-api";

    return {
        plugins: [react()],
        define: {
            "import.meta.env.VITE_CLERK_PUBLISHABLE_KEY": JSON.stringify(clerkPublishableKey),
            "import.meta.env.VITE_CLERK_JWT_TEMPLATE": JSON.stringify(clerkJwtTemplate),
        },
        resolve: {
            alias: {
                "@": fileURLToPath(new URL("./src", import.meta.url)),
            },
        },
        server: {
            host: "0.0.0.0",
            port: 5173,                                          // ← do NOT change to 5000 (see header #1)
            strictPort: true,                                    // fail loudly if 5173 is taken
            allowedHosts: [
                ".replit.dev",                                   // workspace preview hostnames
                ".repl.co",                                      // legacy preview hostnames
                ".kirk.replit.dev",                              // alternate workspace cluster
                "localhost",
                "127.0.0.1",
            ],
            proxy: {
                "/api": {
                    target: `http://localhost:${backendPort}${backendContextPath}`,
                    changeOrigin: true,
                    secure: false,
                },
            },
        },
        build: {
            // Standalone artifact uploaded to S3 and served by CloudFront on AWS.
            // This previously wrote straight into the Spring static directory so
            // the Replit Reserved VM could serve the SPA from the jar. On AWS the
            // SPA and the API are separate origins joined by one CloudFront
            // distribution (default behaviour -> S3, /api/* and /actuator/* ->
            // ALB), so the browser still sees a single origin and
            // runtimeConfig.apiBaseUrl stays empty.
            //
            // Replit is still serving production from this branch, so
            // scripts/replit-build.sh copies dist/ into the Spring static
            // directory afterwards. Remove that copy only when the Replit
            // deployment is retired.
            outDir: "dist",
            emptyOutDir: true,
        },
        preview: {
            host: "0.0.0.0",
            port: 5173,
            allowedHosts: [".replit.dev", ".repl.co", ".kirk.replit.dev", "localhost"],
        },
    };
});
