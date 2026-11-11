# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository. The `.claude/`
tree holds the shared AiDigital engineering contract (rules, agent docs, skills); the facts specific to
this repository live in `.claude/agent_docs/project_facts.md`.

## Project Overview

AIAE Presentation Builder turns a campaign's media plan, Elevate delivery data and optional paid-media
exports into a reviewable Google Sheet and end-of-campaign / end-of-month Google Slides decks
(Standard and MPO designs), with AI-written narrative copy.

- `backend/` — Java 21, Spring Boot 3.5, multi-module Maven backend
  (`domain`, `migrations`, `event-logging-to-db-feature`, `service`, `external-services`, `application`)
- `frontend/` — React + TypeScript + Vite frontend
- `.claude/` — self-contained Claude rules, docs, skills, and task artifacts for this repository

## Start Here

1. Read `.claude/agent_docs/project_facts.md`, then `.claude/agent_docs/index.md`.
2. For backend work, read the relevant docs before changing code:
   - `project_structure.md`
   - `building_the_project.md`
   - `running_tests.md`
   - `code_conventions.md`
   - `database_schema.md`
   - `service_architecture.md`
   - `performance_engineering.md` for request flows, transactions, queries,
     external calls, logging, caching, pools, or payload work
3. For frontend work, read the relevant docs before changing code:
   - `project_structure.md`
   - `building_the_project.md`
   - `running_tests.md`
   - `frontend_architecture.md`
   - `frontend_style.md`
   - `frontend_testing.md`
   - `performance_engineering.md` for data fetching, request counts, payloads,
     rendering, or bundle work
4. Respect `.claude/rules/*.md`.
5. Read `.claude/agent_docs/skill-selection.md` before choosing between `task-workflow` and a focused
   skill. Finish implementation work with `verification-gate`.
6. Before applying a path, package, port, module, navigation, or test command from the shared docs,
   check it against `project_facts.md` and the current repository.

## Enterprise Hard Constraints

- Keep the rule set self-contained; do not depend on another local checkout or a machine-specific
  absolute path.
- Read `.claude/agent_docs/project_shape_decision.md` before deciding frontend-only vs full-stack work.
- Do not hand-edit generated backend OpenAPI sources or generated frontend OpenAPI types.
- Do not add dependencies casually. Use the existing stack and local patterns first.
- Keep secrets out of frontend code and public environment variables.
- Never replace an established product flow, navigation model, or visual system with a template
  default unless the user explicitly asks for that change.

## Backend Hard Constraints

- Backend stack is fixed: Java 21, Spring Boot 3.x, Maven multi-module, PostgreSQL, Liquibase.
- The single production package root is `com.aidigital.reportconstructor`; do not introduce a second
  root or placeholder packages.
- Every new Liquibase `changeSet` declares direct `preConditions`; never rewrite applied changelogs.
- Backend controllers implement generated OpenAPI interfaces and stay thin.
- Backend JPA repositories are accessed only through their paired entity service.
- Outbound integrations (Anthropic, Google, Clerk) live in `backend/external-services`. Spring HTTP
  clients come from `PooledRestClientFactory` (`externalservices.common.http`); Google SDK calls go
  through `GoogleCallTimer` / `GoogleRequestRetrier`; both record on `backend/observability`'s
  `external.client.requests` timer.
- Backend tests follow the project style from `.claude/rules/20-tests.md`.

## Frontend Hard Constraints

- Frontend stack is fixed: React, TypeScript, Vite, TanStack Query, Clerk, `openapi-fetch`, plain CSS
  with BEM.
- Frontend API calls go through the generated OpenAPI client boundary under `frontend/src/shared/api`.
- Navigation is the existing top-header shell (`frontend/src/app/AppShell.tsx` +
  `frontend/src/shared/ui/AppHeader.tsx`); preserve it unless the product owner asks for a different
  model.
- Frontend styles follow BEM and the semantic tokens in `frontend/src/shared/ui/base/tokens.css` and
  `aidigital-tokens.css`; do not introduce Tailwind, CSS Modules, styled-components, Emotion, or
  CSS-in-JS.
- Frontend tests follow the project style from `.claude/rules/50-frontend-tests.md`.
