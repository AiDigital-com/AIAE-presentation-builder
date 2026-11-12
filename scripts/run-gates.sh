#!/usr/bin/env bash
#
# run-gates.sh — BLOCKING run of the AIAE gates. Exits 1 when any gate fails.
#
# structure-lint.sh and verify-gates.sh fail fast on their first violation, which hides every later result, so
# this script runs both orchestrators and then every checker under scripts/lib/ independently, with the same
# arguments the orchestrators pass, and prints one line per gate before deciding the exit code.
#
# Carried failures (scripts/gates-carried.sh) are the only exception, and they never switch a gate off:
# verify-gates.sh runs against a throwaway copy of the working tree with just those conditions neutralised, and a
# carried condition that no longer exists fails the run until its entry is removed.
#
# Requires a real python3 on PATH (the Windows Store stub prints "Python" and exits 0, which would make every
# Python checker look green) and git (the copy holds tracked and unignored files only).

set -uo pipefail

cd "$(dirname "$0")/.."
ROOT="$(pwd)"

# shellcheck source=scripts/gates-carried.sh
. scripts/gates-carried.sh

OPENAPI="backend/application/src/main/resources/api/v1/specs/openapi.yaml"
SCHEMA="frontend/src/shared/api/generated/schema.d.ts"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "${WORK_DIR}"' EXIT

names=()
results=()
failed=0

record() {
  names+=("$1")
  results+=("$2")
  if [ "$2" = "FAIL" ]; then
    failed=$((failed + 1))
  fi
}

run_gate() {
  local label="$1"
  shift
  local log="${WORK_DIR}/${#names[@]}.log"
  if "$@" >"${log}" 2>&1; then
    record "${label}" "PASS"
  else
    record "${label}" "FAIL"
    echo "---- ${label} ----"
    tail -n 15 "${log}"
    echo
  fi
}

# Copies the tracked and unignored files of the working tree (uncommitted edits included) to $1.
copy_working_tree() {
  local target="$1"
  mkdir -p "${target}"
  git ls-files -z --cached --others --exclude-standard \
    | while IFS= read -r -d '' path; do
        if [ -e "${path}" ]; then
          printf '%s\0' "${path}"
        fi
      done \
    | tar --null -T - -cf - \
    | tar -xf - -C "${target}"
}

# verify-gates.sh with the carried conditions neutralised in a copy; a stale carry is itself a failure.
run_verify_gates() {
  local id
  local stale=()
  for id in "${CARRIED_IDS[@]}"; do
    if ! "carried_present_${id}"; then
      stale+=("${id}")
    fi
  done
  if [ "${#stale[@]}" -gt 0 ]; then
    echo "run-gates: stale carry ${stale[*]}: the condition is fixed — remove it from scripts/gates-carried.sh" \
      "and close the decision in docs/aiae-migration-plan.md." >&2
    return 1
  fi
  if [ "${#CARRIED_IDS[@]}" -eq 0 ]; then
    bash scripts/verify-gates.sh
    return
  fi
  local copy="${WORK_DIR}/carried-copy"
  copy_working_tree "${copy}" || return 1
  for id in "${CARRIED_IDS[@]}"; do
    (cd "${copy}" && "carried_neutralise_${id}") || return 1
  done
  echo "run-gates: verify-gates.sh on a copy with carried ${CARRIED_IDS[*]} neutralised"
  VERIFY_ROOT="${copy}" bash "${ROOT}/scripts/verify-gates.sh"
}

if ! python3 -c 'import sys; assert sys.version_info >= (3, 9)' >/dev/null 2>&1 \
    || [ "$(python3 -c 'print(42)' 2>/dev/null)" != "42" ]; then
  echo "run-gates: python3 is missing or is the Windows Store stub; Python gates cannot run." >&2
  echo "run-gates: install Python 3.9+ and expose it as python3 on PATH." >&2
  exit 1
fi

run_gate "structure-lint.sh"                   bash scripts/structure-lint.sh
run_gate "verify-gates.sh"                     run_verify_gates
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

echo "==> AIAE gates"
for i in "${!names[@]}"; do
  printf '    %-4s  %s\n' "${results[$i]}" "${names[$i]}"
done
if [ "${#CARRIED_IDS[@]}" -gt 0 ]; then
  echo "    carried (scripts/gates-carried.sh): ${CARRIED_IDS[*]}"
fi
if [ "${failed}" -gt 0 ]; then
  echo "==> ${failed} of ${#names[@]} gate(s) failing"
  exit 1
fi
echo "==> all ${#names[@]} gates passing"
