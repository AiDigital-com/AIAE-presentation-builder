#!/usr/bin/env bash
#
# gitops-publish.sh — publish one application's change to a shared GitOps
# repository, safely.
#
# Every AIAE application writes to the same AIAE-helm-versions/main and
# AIAE-helm/{dev,prod} branches from its OWN repository. GitHub `concurrency`
# groups are scoped to a repository, so they cannot serialize these writes:
# two applications can clone the same head, edit different files, and race.
#
# This publishes with a bounded optimistic loop — fetch the current remote head,
# reapply only this application's change on top of it, push fast-forward-only,
# and on rejection start again from the new head. It never force-pushes: a
# forced write to a shared branch silently discards another application's
# deployment.
#
# Usage:
#   gitops-publish.sh <repo-slug> <branch> <commit-message> <mutate-command...>
#
# The mutate command runs with CWD set to a clean checkout of <branch> and must
# change ONLY this application's files. It may run several times, so it has to
# be idempotent.
#
# Requires GITOPS_TOKEN in the environment. The token is passed to git through
# a credential helper on stdin, never in the remote URL, so it cannot leak into
# `git remote -v`, the process table, or error output.

set -euo pipefail

REPO_SLUG="${1:?repository slug required, e.g. AiDigital-com/AIAE-helm-versions}"
BRANCH="${2:?branch required}"
COMMIT_MESSAGE="${3:?commit message required}"
shift 3
[ "$#" -gt 0 ] || { echo "gitops-publish: a mutate command is required" >&2; exit 2; }

: "${GITOPS_TOKEN:?GITOPS_TOKEN must be set}"

MAX_ATTEMPTS="${GITOPS_PUBLISH_MAX_ATTEMPTS:-5}"
# Overridable so the optimistic loop can be exercised against a local bare repo
# in tests. Production always uses the default.
REMOTE_BASE="${GITOPS_PUBLISH_REMOTE_BASE:-https://github.com}"
WORKDIR="$(mktemp -d)"
trap 'rm -rf "${WORKDIR}"' EXIT

git_c() {
  git -c credential.helper= \
      -c "credential.https://github.com.helper=!f() { echo username=x-access-token; echo password=\${GITOPS_TOKEN}; }; f" \
      "$@"
}

attempt=1
while [ "${attempt}" -le "${MAX_ATTEMPTS}" ]; do
  rm -rf "${WORKDIR}/repo"

  # Always start from the CURRENT remote head, never from a cached checkout:
  # a stale base is exactly how one application reverts another's entry.
  git_c clone --quiet --depth 1 --branch "${BRANCH}" \
    "${REMOTE_BASE}/${REPO_SLUG}.git" "${WORKDIR}/repo"

  base_sha="$(git -C "${WORKDIR}/repo" rev-parse HEAD)"
  echo "gitops-publish: attempt ${attempt}/${MAX_ATTEMPTS} on ${REPO_SLUG}@${BRANCH} base ${base_sha:0:12}" >&2

  ( cd "${WORKDIR}/repo" && "$@" )

  if [ -z "$(git -C "${WORKDIR}/repo" status --porcelain)" ]; then
    echo "gitops-publish: no change to publish; the desired state is already committed" >&2
    echo "${base_sha}"
    exit 0
  fi

  git -C "${WORKDIR}/repo" add -A
  git -C "${WORKDIR}/repo" -c user.name="aiae-deploy-bot" \
      -c user.email="aiae-deploy-bot@users.noreply.github.com" \
      commit --quiet -m "${COMMIT_MESSAGE}"

  # No --force and no --force-with-lease: a non-fast-forward must fail so the
  # loop can rebuild the change on top of whatever landed meanwhile.
  if git_c -C "${WORKDIR}/repo" push --quiet origin "HEAD:${BRANCH}"; then
    published_sha="$(git -C "${WORKDIR}/repo" rev-parse HEAD)"
    echo "gitops-publish: published ${published_sha}" >&2
    # stdout carries only the SHA, so the caller can pin it downstream
    echo "${published_sha}"
    exit 0
  fi

  echo "gitops-publish: non-fast-forward, another application published first; retrying from the new head" >&2
  attempt=$((attempt + 1))
  sleep $(( attempt * 2 ))
done

echo "gitops-publish: failed to publish after ${MAX_ATTEMPTS} attempts" >&2
exit 1
