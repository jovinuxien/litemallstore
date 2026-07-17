# litemall docker-compose stacks

## Opt-in stacks (NOT part of the default local bring-up)

### Kafka (`docker-compose.kafka.yml`)

Single-node Kafka + Zookeeper for the order/promotion domain events.

```
docker compose -f docker-compose/docker-compose.kafka.yml up -d
```

### Marketing stack (`docker-compose.marketing.yml`, Wave 6)

Matomo (analytics) + its MariaDB, Mautic (campaign automation) + its MySQL, and
MailHog (dev SMTP sink, `1025` SMTP / `8025` UI). **Opt-in** — `dc-local.sh` and
the default bring-up never start it; every litemall service boots and runs with
this stack absent (the marketing ACLs and customer mail are disabled by default).

```
docker compose -f docker-compose/docker-compose.marketing.yml up -d
```

| service | host port (env-overridable)      | first-run setup |
|---------|----------------------------------|-----------------|
| Matomo  | `http://localhost:8095` (`MATOMO_PORT`)  | web installer (DB step pre-filled) → create site **"litemall storefront"** → mint an auth token; record per `litemall-promotion-service/docs/handoff-matomo-tracker.md` |
| Mautic  | `http://localhost:8096` (`MAUTIC_PORT`)  | web installer (DB pre-wired) → add the `litemall_user_id` custom field + Basic-auth API user per `litemall-promotion-service/docs/phase3-marketing-stack-integration.md` |
| MailHog | SMTP `1025` / UI `http://localhost:8025` (`MAILHOG_SMTP_PORT`/`MAILHOG_UI_PORT`) | none |

DB credentials/ports are env-configurable (see the file header); data lives on
dedicated named volumes (`litemall-matomo-*`, `litemall-mautic-*`) so `down`
without `-v` keeps the installed state.

One MailHog serves every worktree — if an ad-hoc `docker run … mailhog/mailhog`
container already holds 1025/8025, the compose `mailhog` service fails its port
bind while the rest of the stack starts normally; either keep using the ad-hoc
one or remove it and re-run `up -d`.

---

# Production stack (`docker-compose.prod.yml`)

The real deployment artifact — all 10 services plus their data plane. See the
repo README for the quick start, and the banner at the top of the file for the
**1-replica constraint** (it is a correctness constraint, not a tuning knob).

```
cp docker-compose/.env.prod.example .env   # fill in; .env is gitignored
docker compose -f docker-compose/docker-compose.prod.yml --env-file .env up -d
```

Every credential is `${VAR:?}` — a missing one aborts the run naming the
variable rather than starting a service with an empty password.

## Search stack (`docker-compose.yml` + `dc-local.sh`)

The OCS tier (indexer / searcher / suggest) and Elasticsearch used for local
development. `dc-local.sh` overlays `docker-compose.local.yml` (Querqy rules,
custom profiles); `dc-cloudconfig.sh` overlays the config-server variants.

```
./docker-compose/dc-local.sh up -d
```

> Both scripts referenced `docker-compose.base.yml` — a file that has never
> existed here (it was renamed `docker-compose.yml` upstream) — so the documented
> bring-up failed outright until Wave 7. Fixed.

---

## Removed in Wave 7

`docker-compose-recover.yml`, `deploy/`, and `docker/litemall/` are gone. They
described the deprecated monolith (`openjdk:8-jre`, a `litemall.jar` no build
produces, `nohup java -jar` on a hardcoded `/home/ubuntu` path) and referenced
`litemall/*` images no script ever built. Anything you need from them lives in
`docker/Dockerfile` and `docker-compose.prod.yml` now.

The original JHipster boilerplate section previously sat here. It documented a
PostgreSQL/Keycloak/`announceService` topology that this project has never run.
