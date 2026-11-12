#!/usr/bin/env bash
#
# gates-report.sh — REPORT-ONLY run of the AIAE gates (phase 1 of the AIAE
# convergence; see docs/architecture-overview.md → "Decisions, constraints").
#
# structure-lint.sh and verify-gates.sh fail fast on their first violation, which
# hides every later result. This script runs both orchestrators and then every
# checker under scripts/lib/ independently, with the same arguments the
# orchestrators pass, and prints one PASS/FAIL line per gate.
#
# It always exits 0: the numbers are a baseline, not a blocker. A later phase
# makes the gates blocking once the backlog is cleared.
#
# Requires a real python3 on PATH (the Windows Store stub prints "Python" and
# exits 0, which would make every Python checker look green).

set -uo pipefail

cd "$(dirname "$0")/.."

OPENAPI="backend/application/src/main/resources/api/v1/specs/openapi.yaml"
SCHEMA="frontend/src/shared/api/generated/schema.d.ts"
LOG_DIR="$(mktemp -d)"
trap 'rm -rf "${LOG_DIR}"' EXIT

names=()
results=()
failed=0

run_gate() {
  local label="$1"
  shift
  local log="${LOG_DIR}/${#names[@]}.log"
  if "$@" >"${log}" 2>&1; then
    names+=("${label}")
    results+=("PASS")
  else
    names+=("${label}")
    results+=("FAIL")
    failed=$((failed + 1))
    echo "---- ${label} ----"
    tail -n 15 "${log}"
    echo
  fi
}

if ! python3 -c 'import sys; assert sys.version_info >= (3, 9)' >/dev/null 2>&1 \
    || [ "$(python3 -c 'print(42)' 2>/dev/null)" != "42" ]; then
  echo "gates-report: python3 is missing or is the Windows Store stub; Python gates cannot run." >&2
  echo "gates-report: install Python 3.9+ and expose it as python3 on PATH." >&2
  exit 0
fi

run_gate "structure-lint.sh"                   bash scripts/structure-lint.sh
run_gate "verify-gates.sh"                     bash scripts/verify-gates.sh
run_gate "check-agent-surfaces"                bash scripts/lib/check-agent-surfaces.sh .
run_gate "check-architecture-overview"         bash scripts/lib/check-architecture-overview.sh
run_gate "check-installed-documentation-links" python3 scripts/lib/check-installed-documentation-links.py .
run_gate "check-api-client-paths"              bash scripts/lib/check-api-client-paths.sh "${OPENAPI}" frontend/src
run_gate "check-openapi-strict-schemas"        bash scripts/lib/check-openapi-strict-schemas.sh "${OPENAPI}" "${SCHEMA}"
run_gate "check-openapi-documentation"         bash scripts/lib/check-openapi-documentation.sh "${OPENAPI}"
run_gate "check-openapi-enums"                 bash scripts/lib/check-openapi-enums.sh "${OPENAPI}"
run_gate "check-openapi-input-constraints"     python3 scripts/lib/check-openapi-input-constraints.py "${OPENAPI}"
run_gate "check-api-validation-tests"          python3 scripts/lib/check-api-validation-tests.py .
run_gate "check-maven-dependency-analysis"     python3 scripts/lib/check-maven-dependency-analysis.py backend
run_gate "check-liquibase-preconditions"       bash scripts/lib/check-liquibase-preconditions.sh
run_gate "check-thin-controllers"              python3 scripts/lib/check-thin-controllers.py backend/application/src/main/java
run_gate "check-frontend-ui-rules"             bash scripts/lib/check-frontend-ui-rules.sh
run_gate "check-production-static-methods"     bash scripts/lib/check-production-static-methods.sh
run_gate "check-production-magic-values"       bash scripts/lib/check-production-magic-values.sh
run_gate "check-production-current-time"       bash scripts/lib/check-production-current-time.sh
run_gate "check-production-manual-mapping"     bash scripts/lib/check-production-manual-mapping.sh
run_gate "check-service-contract-quality"      bash scripts/lib/check-service-contract-quality.sh
run_gate "check-coverage-integrity"            bash scripts/lib/check-coverage-integrity.sh

echo "==> AIAE gates (report-only)"
for i in "${!names[@]}"; do
  printf '    %-4s  %s\n' "${results[$i]}" "${names[$i]}"
done
echo "==> ${failed} of ${#names[@]} gate(s) failing — report-only, exit 0"
exit 0
