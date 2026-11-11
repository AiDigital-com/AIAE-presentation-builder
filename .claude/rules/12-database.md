---
description: Backend database, Liquibase, and persistence rules.
paths:
  - "backend/migrations/**/*"
  - "backend/domain/src/main/java/**/*.java"
  - "backend/domain/src/test/java/**/*.java"
  - "backend/service/src/main/java/**/*.java"
---

# Backend Database Rules

- PostgreSQL only.
- New schema changes go through Liquibase under `backend/migrations/src/main/resources/db/changelog`.
- Do not rewrite existing applied changelogs unless the user explicitly asks for it.
- Every Liquibase `changeSet` must declare direct `preConditions`; use `onFail="MARK_RAN"` plus existence checks for idempotent create-table/create-index changes. Applies to new changeSets; the already-applied `0001`/`0002` changelogs predate the rule and must not be rewritten (see `.claude/agent_docs/project_facts.md` → Known gaps).
- Database identifiers use Java `Long` and PostgreSQL `BIGINT`.
- Text columns use PostgreSQL `TEXT`, not `VARCHAR`.
- Entity equality/hash code must be based on the persistent identifier.
- Each entity owns one repository in `backend/domain` and one paired entity service in `backend/service/entity`.
- Repository access is centralized through the paired entity service; higher-level services do not bypass that boundary.
- Hibernate L2/query cache uses Ehcache/JCache configured at application level, not ad-hoc service-local caching.
- Hikari, JPA, Liquibase, and cache defaults are configured through application configuration, not scattered across services.
- Repository calls and lazy-association traversal inside loops are forbidden;
  use set-based/batched queries and verify query counts for affected workflows.
- Collection reads are bounded and use summary projections/DTOs when the caller
  does not need detail graphs. Filter, sort, group, and page growing data in
  PostgreSQL rather than in memory.
- Fetch joins with pagination require an explicit correctness review for row
  multiplication, multiple bags, in-memory pagination, and count-query cost.
- Add an index only after capturing the exact query shape and verifying a
  representative plan; review overlapping indexes and write cost.
- Do not increase Hikari limits or enable global JDBC batching, eager fetching,
  or additional Hibernate caching without runtime evidence.
