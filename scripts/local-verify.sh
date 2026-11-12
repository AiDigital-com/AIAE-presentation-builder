#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/replit-env.sh

# Backend: build, test and enforce coverage for the committed phase (.template-phase). `mvp` passes -Pmvp —
# the relaxed floor plus each module's ratchet; `engineering` runs the strict 0.80 / 0.70 gate.
. scripts/lib/coverage-phase.sh
phase="$(coverage_phase_read .)"
coverage_phase_announce "$phase"
read -ra coverage_args <<< "$(coverage_phase_maven_args "$phase")"
mvn -f backend/pom.xml -B ${coverage_args[@]+"${coverage_args[@]}"} verify

cd frontend
if [ -f package-lock.json ]; then npm ci; else npm install; fi
npm run generate:api
npm run typecheck
npm run lint
npm run build

# AIAE gates — report-only for now (always exits 0); see docs/aiae-migration-plan.md.
cd ..
bash scripts/gates-report.sh
