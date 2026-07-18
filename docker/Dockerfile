# syntax=docker/dockerfile:1.7
#
# One file, one reactor build, ten runtime images.
#
# Why a single shared Dockerfile instead of ten: the reactor is one Maven build
# and both SPAs are bound into it via frontend-maven-plugin. Ten independent
# Dockerfiles would each run a full reactor + npm build (~10x the work) and could
# drift from each other. Here the `builder` stage runs ONCE and every service
# stage copies its jar out of it.
#
# Build a single service:   docker build -f docker/Dockerfile --target litemall-order .
# Build all ten:            docker compose -f docker-compose/docker-compose.prod.yml build
#
# Replaces docker/litemall/Dockerfile, which packaged the DEPRECATED monolith on
# EOL openjdk:8-jre with an unbounded `java -jar`.

# ---------------------------------------------------------------------------
# Stage 1 — builder. Runs the whole reactor (Java + both SPAs) exactly once.
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

COPY . .

# TWO PASSES, deliberately. `mvn package` cannot build this reactor from a clean
# checkout in one go:
#
#   litemall-goods-management's `webapp` profile (activeByDefault) runs
#   `npm run build` -> `vite build`, but vite is in NO dependency list and the
#   package.json is JHipster scaffolding ("example project for testing
#   frontend-maven-plugin"). It exits 127 and takes the reactor down. goods-
#   management serves no SPA, so this build produces nothing anyone consumes.
#
# Neither global switch works alone:
#   -Dskip.npm=true   also skips the TWO REAL SPAs (gateway-api's storefront and
#                     gateway-admin's console) -> jars silently ship with no UI.
#   -P '!webapp'      also disables gateway-admin's real SPA, which is built by
#                     its own `webapp` profile.
#
# So: pass 1 builds every jar with npm off and installs them to the local repo;
# pass 2 rebuilds ONLY the two gateways with their SPAs, resolving deps from
# pass 1. Verified below — a gateway jar without its SPA is a known silent
# failure mode in this repo, so we assert the bundle exists rather than trust it.
#
# The proper fix is one line in litemall-goods-management (drop the dead webapp
# profile); that module is PARKED, hence the workaround here.
#
# Tests run in CI, not here — a failing test must fail the PR, not the image
# build. The cache mount keeps ~/.m2 out of the image layers and off the host's
# shared ~/.m2, which concurrent worktree builds race.
# -Dmaven.test.skip=true (not just -DskipTests): images must not COMPILE tests
# either. -DskipTests alone still runs testCompile, which would drag the test
# tree — and its testcontainers/mockito deps — into the image build. It would
# also make the deploy artifact hostage to a broken test tree: litemall-order's
# tests do not currently compile (see docs/handoff-secrets-wave7.md), and that
# must not be able to stop a deploy. Tests are CI's job, and CI runs them.
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -q -Dmaven.test.skip=true -Dskip.npm=true -Dskip.installnodenpm=true install

# `clean` is REQUIRED here, not tidiness. gateway-admin gates its npm goals on a
# folder checksum: maven-antrun `eval-frontend-checksum` sets skip.npm=true when
# target/checksums.csv matches checksums.csv.old. Pass 1 above writes those
# checksums, so without `clean` pass 2 concludes "webapp unchanged", SKIPS every
# npm goal (each execution logs but exits in ~5ms), and produces an admin jar
# with NO UI — silently, with a green build. That is the documented
# "checksum dirty-skip ships a jar w/o SPA" trap. The assertion below is what
# catches it if this ever regresses.
#
# Not -q: the SPA build's own output is the only diagnostic when webpack fails,
# and a silent failure here is what ships a gateway with no UI.
#
# The npm cache mount matters more than it looks. There is no lockfile at the
# workspace root, so `npm install` re-resolves ALL THREE workspace members from
# scratch — including gateway-admin's 113-package JHipster tree — on every build.
# Uncached that is tens of minutes and is at the mercy of registry throughput.
# BOTH flags, and they are not redundant:
#   -Dmaven.test.skip=true  stops test COMPILATION (images must not build tests).
#   -DskipTests             is what frontend-maven-plugin actually honours for
#                           executions bound to the `test` phase. gateway-admin
#                           has a "webapp build test" execution (npm run
#                           webapp:test) there; it does NOT respect
#                           maven.test.skip, so with only the first flag it RUNS
#                           its jest suite during the image build and fails.
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    --mount=type=cache,target=/root/.npm,sharing=locked \
    mvn -B -Dmaven.test.skip=true -DskipTests clean package \
        -pl litemall-gateway-api,litemall-gateway-admin

# Fail the build here rather than ship a storefront with no UI.
RUN set -eu; \
    for j in litemall-gateway-api/target/litemall-gateway-api-*.jar \
             litemall-gateway-admin/target/litemall-gateway-admin-*.jar; do \
      if ! jar tf "$j" | grep -qE 'BOOT-INF/classes/static/.+\.(js|html)'; then \
        echo "FATAL: $j contains no built SPA (BOOT-INF/classes/static)."; \
        echo "       The npm build was skipped or emitted nothing."; \
        exit 1; \
      fi; \
      echo "OK: SPA present in $j"; \
    done

# ---------------------------------------------------------------------------
# Stage 2 — runtime base. Shared by all ten services.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime

# curl backs the compose healthchecks; the JRE image has no HTTP client.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl tini \
 && rm -rf /var/lib/apt/lists/*

# Non-root. GID 0 so an arbitrary runtime UID can still read /app if the
# platform reassigns it.
RUN useradd --uid 1001 --gid 0 --shell /usr/sbin/nologin --create-home litemall
WORKDIR /app

# Writable log dir owned by the runtime user. goods-management's
# logback-spring.xml writes ${log.path}/log.log with log.path="logs" (a plain
# <property>, not overridable from yaml), which resolves to /app/logs. Without
# this the service dies at startup — FileNotFoundException: logs/error.log —
# because /app is root-owned and uid 1001 cannot create the dir. Harmless for the
# services that log only to stdout. goods-management is a parked module, so the
# fix lives here rather than in its source.
RUN mkdir -p /app/logs && chown 1001:0 /app/logs && chmod 775 /app/logs

USER 1001

# MaxRAMPercentage makes the JVM honour the compose mem_limit. Without it the
# JVM sizes its heap from the HOST's RAM and the container gets OOM-killed under
# load — the specific failure the old `java -jar` entrypoint invited.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

# tini reaps zombies and forwards SIGTERM, so `docker compose stop` is a clean
# Spring shutdown rather than a 10s kill.
ENTRYPOINT ["/usr/bin/tini", "--", "sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

# ---------------------------------------------------------------------------
# Stage 3 — one thin stage per service.
#
# Ports are the service's own configured server.port (see each application.yml);
# EXPOSE is documentation — compose does the actual publishing.
#
# WHY SOME COPY *-exec.jar AND OTHERS DON'T — do not "tidy" this into one glob.
#
# order, goods-management, promotion-service and loyalty-service bind
# spring-boot-maven-plugin with <classifier>exec</classifier> (a JHipster
# convention). Those four modules emit TWO jars:
#
#   litemall-<mod>-0.0.1-SNAPSHOT.jar        ~0.5MB  plain library jar, NO Main-Class
#   litemall-<mod>-0.0.1-SNAPSHOT-exec.jar   ~110MB  the bootable fat jar
#
# A `litemall-<mod>-*.jar` glob matches BOTH and lands the plain one on /app/app.jar,
# so the container starts and dies instantly with:
#
#   no main manifest attribute, in /app/app.jar
#
# eureka, config, authserver and both gateways use no classifier and emit a single
# bootable jar, so their plain glob is correct. Adding -exec there would break them.
# ---------------------------------------------------------------------------

FROM runtime AS litemall-eureka
COPY --from=builder --chown=1001:0 /build/litemall-eureka/target/litemall-eureka-*.jar /app/app.jar
EXPOSE 8761

FROM runtime AS litemall-config
COPY --from=builder --chown=1001:0 /build/litemall-config/target/litemall-config-*.jar /app/app.jar
EXPOSE 8888

FROM runtime AS litemall-authserver
COPY --from=builder --chown=1001:0 /build/litemall-authserver/target/litemall-authserver-*.jar /app/app.jar
EXPOSE 8089

FROM runtime AS litemall-gateway-api
COPY --from=builder --chown=1001:0 /build/litemall-gateway-api/target/litemall-gateway-api-*.jar /app/app.jar
EXPOSE 8090

FROM runtime AS litemall-gateway-admin
COPY --from=builder --chown=1001:0 /build/litemall-gateway-admin/target/litemall-gateway-admin-*.jar /app/app.jar
EXPOSE 8080

FROM runtime AS litemall-order
COPY --from=builder --chown=1001:0 /build/litemall-order/target/litemall-order-*-exec.jar /app/app.jar
EXPOSE 8085

FROM runtime AS litemall-goods-management
COPY --from=builder --chown=1001:0 /build/litemall-goods-management/target/litemall-goods-management-*-exec.jar /app/app.jar
EXPOSE 8082

FROM runtime AS litemall-promotion-service
COPY --from=builder --chown=1001:0 /build/litemall-promotion-service/target/litemall-promotion-service-*-exec.jar /app/app.jar
EXPOSE 8088

FROM runtime AS litemall-loyalty-service
COPY --from=builder --chown=1001:0 /build/litemall-loyalty-service/target/litemall-loyalty-service-*-exec.jar /app/app.jar
EXPOSE 8087

# NOTE: there is deliberately no litemall-goodsapi-analytic stage. That module is
# excluded from the reactor (see the comment in pom.xml) because it does not
# compile and would register in Eureka under goods-management's name.
