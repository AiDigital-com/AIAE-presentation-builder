# Liquibase.Dockerfile — database migration image.
#
# Built and pushed alongside the application image on every deployment, tagged
# `liquibase-<same-version>-<same-commit>` so the schema and the code that
# depends on it are always a matched, immutable pair.
#
# Argo CD runs this image as a PreSync Job (see AIAE-helm), which must succeed
# before any new application pod rolls out. The runtime image never migrates:
# application-eks.yml sets spring.liquibase.enabled=false.
#
# The Job invokes:
#   mvn -f backend/pom.xml -pl db liquibase:update \
#       -Dliquibase.url=... -Dliquibase.username=... -Dliquibase.password=...
# with credentials read from the Secrets Store CSI mount, so they never appear
# in the image, in Helm values, or in the process arguments of any other pod.
#
# Build context is the REPO ROOT, matching backend/Dockerfile.

FROM maven:3.9-eclipse-temurin-21

WORKDIR /workspace

COPY backend/ ./backend/

# `install` (not `package`) so the reactor's parent POM and the db artifact are
# resolvable from the local repository when the Job later runs a single-module
# `-pl db` build.
#
# `liquibase:help` then pre-downloads the plugin and its PostgreSQL driver into
# /root/.m2. Without it the Job's first act is a cold dependency download, which
# turns a transient Maven Central outage into a failed deployment gate.
RUN mvn -f backend/pom.xml -B -pl db -am -DskipTests -Dskip.frontend=true install \
 && mvn -f backend/pom.xml -B -pl db liquibase:help -Ddetail=false > /dev/null

# No ENTRYPOINT/CMD: the Job supplies the full command so the changelog is never
# applied merely by starting a container from this image.
