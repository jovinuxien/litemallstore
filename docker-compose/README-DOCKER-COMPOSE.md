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

# JHipster generated Docker-Compose configuration

## Usage

Launch all your infrastructure by running: `docker compose up -d`.

## Configured Docker services

### Service registry and configuration server:

- [JHipster Registry](http://localhost:8761)

### Applications and dependencies:

- gateway (gateway application)
- gateway's postgresql database
- gateway's elasticsearch search engine
- announceService (microservice application)
- announceService's postgresql database
- announceService's elasticsearch search engine
- orderService (microservice application)
- orderService's postgresql database
- catalogService (microservice application)
- catalogService's postgresql database

### Additional Services:

- Kafka
- Zookeeper
- [Keycloak server](http://localhost:9080)
