# Maven dependency analysis

`mvn verify` runs `maven-dependency-plugin:analyze-only` in every backend module
(`backend/pom.xml`, execution `analyze-declared-dependencies`) with
`failOnWarning=true` and `ignoreNonCompile=true`. The build fails when a module

- compiles against a library it does not declare (used-undeclared), or
- declares a compile dependency its bytecode never uses (unused-declared), or
- declares a compile dependency that only its tests use.

## How the modules declare dependencies

- **Library modules** (`domain`, `observability`, `event-logging-to-db-feature`,
  `service`, `external-services`) declare the libraries they compile against
  directly — `spring-context`, `hibernate-core`, `spring-data-jpa`, … — not
  Spring Boot starters. Starters are runtime assemblies, not APIs.
- **`application`** declares the libraries its own code compiles against at
  compile scope, and every starter (`web`, `security`, `oauth2-resource-server`,
  `aop`, `data-jpa`, `validation`, `actuator`, springdoc, Logbook, logstash) at
  `runtime` scope. `observability` and `migrations` are `runtime` too: no
  application class references them, but their beans and the Liquibase changelogs
  must be in the fat jar. `external-services` is not declared at all: it is a leaf
  that `service` depends on, so it reaches the fat jar through `service`.
- **`jakarta.servlet-api`** is `provided` where it is compiled against; the servlet
  container (Tomcat) arrives at runtime through `spring-boot-starter-web`.
- **`migrations`** ships only changelogs; `liquibase-core` is `runtime`.
- **Tests** keep `spring-boot-starter-test` and additionally declare the test
  libraries they compile against (`junit-jupiter-api`, `mockito-core`,
  `assertj-core`, …). A library used only by tests is `test` scope.

## Allowlist

`ignoredUnusedDeclaredDependencies` contains exactly two coordinates:

- `org.projectlombok:lombok` — required at compile time for its annotation
  processor, while the compiled classes no longer reference the Lombok JAR.
  Every module declares it per the backend hard rules.
- `${project.groupId}:event-logging-to-db-feature` — the AIAE contract
  (`scripts/structure-lint.sh`) requires `service` to attach the MVP usage-telemetry
  module while it exists, so services can use `@LogUsage` / `UsageAttributes`.
  `service.integration.SheetQueryServiceImpl` uses `@LogUsage` today; the entry stays
  so removing that use never breaks the build. Remove it together with the module at
  engineering handoff.

Runtime and test dependencies are excluded by scope (`ignoreNonCompile=true`);
never copy them into this allowlist. If a library is legitimately reached only
through reflection or annotation processing, document the exact coordinate and
reason here, then add only that coordinate to `backend/pom.xml`.
