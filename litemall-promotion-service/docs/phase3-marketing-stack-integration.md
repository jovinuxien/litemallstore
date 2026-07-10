# Phase 3 — External marketing-stack integration (Matomo in · Nutch in · Mautic out)

Turns the Phase-2 targeting engine into a closed loop: pull statistics **in** from
Matomo, optionally enrich **in** from a Nutch crawl, and deliver targeting
decisions **out** to Mautic. Every integration sits behind an
**anti-corruption layer** under `infrastructure/acl/**` and is reached only
through an **application port** — domain code never imports an external client
(the same boundary discipline as goods-management's OCS ACL).

All hosts/tokens come from config (`${ENV:default}` in `config/application.yml`),
every integration is **disabled by default**, and every adapter **degrades
gracefully** (returns empty / no-ops + logs) so the service boots and campaigns
evaluate with or without the external systems running.

```
 Matomo Reporting API ─▶ MatomoStatisticsAdapter ─┐
 litemall-order (Feign) ─▶ OrderStatisticsAdapter ─┼─[stats.source]─▶ CustomerStatisticsProvider ─▶ targeting engine
                                                   │                                                      │
 Nutch → Solr index ────▶ NutchCrawlIngestAdapter ─┴─▶ CrawledMarketDataProvider ─(enrichment)───────────┤
                                                                                                          ▼
                                                                                          PromotionTargetedEvent (AFTER_COMMIT)
                                                                                                          │
                                                                MauticTargetingDeliveryListener ─▶ CampaignDeliveryPort
                                                                                                          │
                                                                                       MauticDeliveryAdapter ─▶ Mautic REST API
```

---

## 1. Matomo (analytics → statistics, IN)

**Port:** `application/ports/CustomerStatisticsProvider` (reused from Phase 2).
**ACL:** `infrastructure/acl/matomo/` — `MatomoReportingClient` (Feign) +
`MatomoStatisticsAdapter` + `dto/MatomoUserStatRow`.

### Source selection — `litemall.promotion.stats.source`
`StatsSourceConfiguration` produces the `@Primary` `CustomerStatisticsProvider`:

| value       | provider                                   | use                                     |
|-------------|--------------------------------------------|-----------------------------------------|
| `order`     | `OrderStatisticsAdapter` (Phase-2 default) | transactional RFM truth via Feign       |
| `matomo`    | `MatomoStatisticsAdapter`                  | analytics-derived engagement/conversion |
| `composite` | `CompositeCustomerStatisticsProvider`      | merge both by user id (see below)        |

**Composite merge rule:** order supplies frequency (order count) and monetary
(spend) — the transactional truth; Matomo augments **recency** (the more recent
activity across both sources wins) and contributes engagement-only customers the
order read model has not yet seen. Union of both populations by user id.

### Reporting-API contract
`GET {base-url}/index.php?module=API&format=json&method={reportMethod}&idSite={siteId}&period={period}&date={date}&token_auth={authToken}&filter_limit={filterLimit}`

Expected: a JSON array of per-user rows keyed by the **litemall user id** — via
Matomo's [User ID](https://matomo.org/docs/user-id/) feature (set `userId` on the
tracker to the litemall id) or a custom dimension. The default `reportMethod`
(`UserId.getUsers`) lists users; a custom report should additionally carry goal
revenue/conversions per user. Window is chosen by Matomo's `period`/`date` (its
reporting granularity differs from the order read model's `since`).

### Field mapping → `CustomerStatistics`
| Matomo field          | domain field           | notes                              |
|-----------------------|------------------------|------------------------------------|
| `label`               | `userId`               | parsed as int; non-numeric → skip  |
| `lastActionTimestamp` | `lastOrderAt` (recency)| epoch seconds → `LocalDateTime`    |
| `nb_conversions`      | `orderCount` (freq)    | null → 0                           |
| `revenue`             | `totalSpend` (monetary)| null/negative → 0                  |

**Degradation:** disabled / transport error / empty payload → empty population →
empty audience (no exception through the admin endpoint).

### Config keys (`litemall.promotion.matomo.*`)
`enabled` · `base-url` · `auth-token` · `site-id` · `report-method` · `period` ·
`date` · `filter-limit`.

---

## 2. Nutch 1.8 (crawl → read model, IN, enrichment)

Nutch runs **outside** this service and indexes crawl results to Solr; this is an
**ingestion ACL only**.

**Port:** `application/ports/CrawledMarketDataProvider`.
**ACL:** `infrastructure/acl/nutch/` — `NutchIndexClient` (Feign → Solr) +
`NutchCrawlIngestAdapter` + `dto/NutchSolrResponse`.
**Read model:** `domain/model/valueobjects/crawled/CrawledProductSignal`.

### Crawl → ingest contract
- **What Nutch crawls:** competitor/market product pages (the seed list / crawl
  scope is a Nutch-side concern, outside this service).
- **Ingest query:** `GET {solr-url}/{core}/select?wt=json&q={query}&rows={rows}` —
  documents under `response.docs`.

### Field mapping → `CrawledProductSignal`
| Solr/Nutch field | domain field   | notes                                        |
|------------------|----------------|----------------------------------------------|
| `url`            | `sourceUrl`    | required; missing → row skipped              |
| `host`           | `host`         |                                              |
| `title`          | `title`        | Solr multi-valued → first element            |
| `content`        | `snippet`      | truncated to 280 chars                        |
| —                | `crawledPrice` | **null today** — price extraction is future enrichment |

### Wiring
Consumed as **optional enrichment** during campaign evaluation
(`LitemallCampaignServiceImpl.evaluateCampaign`): when enabled, signals are
fetched and their count surfaced on the evaluation result (`crawledSignalCount`)
and logged. Deeper use (e.g. a price-competitiveness criterion) is future work.

**Degradation:** disabled / transport error / empty payload → no signals →
evaluation proceeds unchanged.

### Config keys (`litemall.promotion.nutch.*`)
`enabled` · `solr-url` · `core` · `query` · `rows`.

---

## 3. Mautic (automation → delivery, OUT)

**Port:** `application/ports/CampaignDeliveryPort`.
**ACL:** `infrastructure/acl/mautic/` — `MauticClient` (Feign, Basic auth via
`MauticFeignConfig`) + `MauticDeliveryAdapter` + DTOs.
**Trigger:** `MauticTargetingDeliveryListener` listens for
`PromotionTargetedEvent` at `@TransactionalEventListener(AFTER_COMMIT)` and calls
the port. The listener lives in `infrastructure/acl/mautic` (not in the domain
event handler) so the external client stays out of `domain/`.

### Event → Mautic mapping (`deliverToAudience`)
1. **Ensure a per-campaign segment** — `POST /api/segments/new`
   `{ name, alias = "{segment-alias-prefix}{campaignId}", isPublished: true }` →
   `response.list.id`.
2. **Upsert each audience contact** — `POST /api/contacts/new`
   `{ "{user-id-field-alias}": <litemallUserId>, tags: "litemall-campaign-{id}" }`
   → `response.contact.id`. (Assumes a Mautic custom contact field with alias
   `litemall_user_id`.)
3. **Add contact to the segment** — `POST /api/segments/{segmentId}/contact/{contactId}/add`.
   A Mautic campaign configured to listen on that segment then fires the
   email/automation.

**Degradation:** disabled / empty audience → no-op; any transport failure is
logged and swallowed so a Mautic outage never unwinds the committed campaign
evaluation.

### Config keys (`litemall.promotion.mautic.*`)
`enabled` · `base-url` · `username` · `password` · `user-id-field-alias` ·
`segment-alias-prefix`.

---

## Verification

Unit tests (no live stack required), run with the project's fork-disabled flow
(see the test-run gotcha — JDWP-suspend fork must be avoided):

```
mvn -q -o -pl litemall-promotion-service -am install -DskipTests=true -Dmaven.test.skip=true
mvn -o -pl litemall-promotion-service test -Dmaven.test.skip=false -DskipTests=false -DforkCount=0 -DfailIfNoTests=false
```

- `MatomoStatisticsAdapterTest` — row → `CustomerStatistics` mapping; disabled / failure → empty.
- `CompositeCustomerStatisticsProviderTest` — order-truth + Matomo-recency merge, union of users.
- `MauticDeliveryAdapterTest` — event → ensure-segment + upsert-contact + add-to-segment; disabled/empty no-op; failure swallowed.
- `NutchCrawlIngestAdapterTest` — Solr doc → `CrawledProductSignal` (incl. multi-valued field); disabled / failure → empty.

### Manual runbook (against live systems)
1. Set the relevant `*_ENABLED=true` env vars plus base-url/token/credentials.
2. **Matomo:** set `PROMOTION_STATS_SOURCE=matomo` (or `composite`), define a
   campaign, `POST /srv/private/admin/promotion/campaign/{id}/evaluate`, confirm
   the population/audience is non-empty and sourced from Matomo.
3. **Nutch:** point `NUTCH_SOLR_URL`/`NUTCH_CORE` at the crawl index; evaluate and
   confirm `crawledSignalCount` > 0 in the response.
4. **Mautic:** with `MAUTIC_ENABLED=true`, evaluate an active campaign and confirm
   a `litemall-campaign-{id}` segment appears in Mautic populated with the audience.

## Boundary check
```
# no external client imported from domain/
grep -rn "infrastructure.acl" litemall-promotion-service/src/main/java/org/linlinjava/litemall/promotion/domain   # → no matches
# no hardcoded hosts/tokens
grep -rn "http://" litemall-promotion-service/src/main/java   # → no matches (hosts resolve from config)
```
