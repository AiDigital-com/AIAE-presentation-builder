#!/usr/bin/env bash
#
# gates-carried.sh — the AIAE gate failures this project deliberately carries, each tied to an open decision in
# docs/aiae-migration-plan.md. Sourced by scripts/run-gates.sh; not run on its own.
#
# verify-gates.sh (AIAE-managed, unchanged) stops at its first failure, so a carried failure would hide every
# check after it. run-gates.sh therefore never skips verify-gates.sh: it runs it against a throwaway copy of the
# repository in which only the carried conditions below are neutralised, so every other assertion still blocks.
#
# Each carried ID defines two functions, both run from the repository root (or the copy):
#   carried_present_<ID>     exits 0 while the condition still exists. When it stops, run-gates.sh fails with a
#                            "stale carry" error: remove the entry here and close the decision in the plan.
#   carried_neutralise_<ID>  rewrites the copy so the condition is gone (never run against the real tree).
#
# Adding an entry needs a decision row in docs/aiae-migration-plan.md. Never carry a failure to get a build green.

CARRIED_IDS=(D2 D3)

# D2 — `.replit` maps Vite's 5173 to an external port. The Clerk dev pattern https://*.replit.dev:5173 relies on
# that URL; remove the mapping once the Replit workspace preview is verified to work without it.
carried_present_D2() { grep -q '^localPort = 5173$' .replit; }
carried_neutralise_D2() { sed -i 's/^localPort = 5173$/# carried D2: localPort 5173/' .replit; }

# D3 — frontend/package.json uses Vitest 4 (7e7d33a); the standard pins the firewall-approved ^3.2.6.
carried_present_D3() { grep -Eq '"vitest"[[:space:]]*:[[:space:]]*"\^4\.' frontend/package.json; }
carried_neutralise_D3() { sed -i -E 's/"vitest"([[:space:]]*):([[:space:]]*)"\^4\.[^"]*"/"vitest"\1:\2"^3.2.6"/' frontend/package.json; }
