---
name: rule-compliance-audit
description: Audit an entire repository or requested area against installed CLAUDE.md, .claude/rules, and agent docs. Use for periodic health checks, migration validation, pre-handoff compliance, or when asked whether a project follows the complete engineering contract.
---

# Rule Compliance Audit

Run a read-only, whole-contract audit. This is broader than code review: it
checks architecture, generated boundaries, configuration, tests, frontend
behavior, documentation, and rule distribution.

## Procedure

1. Read `CLAUDE.md`, `.claude/agent_docs/index.md`, `.claude/agent_docs/project_facts.md`, and every
   applicable rule. Report items listed under "Known gaps" in `project_facts.md` as known, tracked gaps
   rather than new findings, unless the gap has grown.
2. Record the audit scope, current branch/diff state, discovered package root,
   modules, build tools, and test layers. Never substitute template assumptions
   for repository facts.
3. Build a rule-to-evidence matrix before reporting findings:
   - rule and source file;
   - files/commands inspected;
   - compliant, violation, accepted exception, or not verified.
4. Run relevant scanners from the specialized backend/frontend review skills.
   Inspect every hit before classifying it.
5. Check cross-cutting risks scanners miss:
   - business-flow and authorization gaps;
   - generated contract drift;
   - repositories bypassed by orchestration;
   - silent failures and broad fallbacks;
   - missing configuration or secrets committed to source;
   - duplicate frontend requests and stale user-scoped cache;
   - unbounded/detail-heavy APIs, N+1/repository-in-loop work, and in-memory
     filtering/pagination;
   - external I/O inside transactions and unsafe timeout/retry ordering;
   - missing Lombok dependencies in any backend Maven submodule;
   - reusable external metrics duplicated outside `backend/observability`, or a
     third-party Spring HTTP client missing either
     `ExternalClientMetricsInterceptor` or `LogbookClientHttpRequestInterceptor`;
   - missing loading/error/empty/success UI states;
   - build/test/CI commands that no longer match repository structure.
6. Run the strongest practical verification commands. Never infer full
   compliance from a partial build or scanner-only pass.

## Classification

- `VIOLATION`: confirmed conflict with an applicable rule.
- `ACCEPTED_EXCEPTION`: explicit, documented project decision with evidence.
- `NOT_VERIFIED`: required runtime/tool/credential unavailable.
- `COMPLIANT`: inspected evidence satisfies the rule.

Report only high-confidence violations. Separate pre-existing debt from changes
in the requested scope. Do not turn every recommendation into a compliance
failure.

## Output

```text
STATUS: COMPLIANT | VIOLATIONS_FOUND | INCOMPLETE
Scope: <whole repo or areas>
Evidence: <commands/files>
Counts: blocking=<n>, important=<n>, minor=<n>, not_verified=<n>
Findings:
- [severity, confidence] rule-source - file:line - violation - impact - fix
Accepted exceptions:
- <decision and evidence>
Not verified:
- <gap and what is required>
```

`COMPLIANT` is allowed only when all applicable blocking rules have evidence.
