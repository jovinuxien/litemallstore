# Phase 2 — Algorithmic Targeting Core (rules + statistics first)

This document records the Phase-2 design of the promotion service's targeting
engine, the mapping from each implemented step back to Katsov's *Introduction to
Algorithmic Marketing* (Ch. 3), the configuration contract, and the open
cross-module follow-up. It mirrors the discipline of the Phase-1
`adr-combination-groupon-split.md`.

## Decision recap (locked)

- **Rules + statistics before ML.** Phase 2 ships deterministic RFM scoring and
  rule-based segmentation. The **audience-selection step is a port**
  (`AudienceSelector`) so a later propensity/uplift/LTV model can slot in without
  reworking callers.
- **Statistics source behind a port.** `CustomerStatisticsProvider` is the seam.
  Phase 2 reads litemall-order via Feign behind an ACL; **Phase 3 swaps in a
  Matomo adapter** behind the same port.
- **No magic numbers.** Every RFM boundary and segment cut-off lives in
  `PromotionTargetingProperties` (`litemall.promotion.targeting.*`),
  profile-overridable via the config server.

## Pipeline and book mapping

| Step | Implementation | Katsov Ch. 3 |
|------|----------------|--------------|
| Customer statistics (R/F/M inputs) | `CustomerStatistics` VO, sourced via `CustomerStatisticsProvider` → `OrderStatisticsAdapter` (Feign ACL) | §3.5 — customer data / LTV building blocks |
| RFM scoring (1..5 per axis) | `RfmScoringService`, config-driven boundaries | §3.5 — RFM as the canonical statistical descriptor |
| Segmentation (rule-based) | `SegmentationService` → `CustomerSegment` | §3.5 — segmentation building block |
| Audience selection (criteria match) | `AudienceSelector` port + `RuleBasedAudienceSelector` (`@Primary`) | §3.6 — product-promotion campaign targeting |
| Campaign definition (criteria + target + linked mechanic + schedule) | `LitemallPromotionCampaignAggregate`, `TargetingCriteria` | §3.6 — campaign as a first-class object |
| Budget / cap | `CampaignBudget` VO, enforced in `LitemallCampaignTargetingDomainService` | §3.6.2 — campaign budgeting |
| Assignment (audience → event) | `PromotionTargetedEvent`, emitted on evaluate | §3.6 — campaign activation / delivery (Phase 3 wires Mautic) |

### Scoring rules

Boundaries are ascending lists of length 4 → five 1..5 buckets.

- **Recency** (`recency-boundaries-days`, default `[30,60,120,240]`): *lower days
  is better*. ≤30d → R5; >240d → R1. Never-ordered → R1 (sentinel recency).
- **Frequency** (`frequency-boundaries`, default `[2,4,7,12]`): *higher is
  better*. ≥12 orders → F5; <2 → F1.
- **Monetary** (`monetary-boundaries`, default `[100,500,1000,5000]`): *higher is
  better*. ≥5000 spend → M5; <100 → M1.

### Segment rules (priority order)

Evaluated against the cut-offs in `litemall.promotion.targeting.segments.*`:

1. **CHAMPIONS** — R, F, M all ≥ `champion-min-score` (4)
2. **AT_RISK** — R ≤ `at-risk-max-recency` (2) **and** F ≥ `at-risk-min-frequency` (3)
3. **LOYAL** — F and M both ≥ `loyal-min-score` (3)
4. **BIG_SPENDER** — M ≥ `big-spender-min-monetary` (4)
5. **NEW** — R ≥ `new-min-recency` (4) **and** F ≤ `new-max-frequency` (1)
6. **HIBERNATING** — everything else

## Surfaces (admin only)

Per the crmeb `*ManagerResponse` vs `*H5Response` split, campaign management is
**admin-only** — there is no customer campaign surface. All endpoints sit under
`/srv/private/admin/promotion/campaign` and are gated `ROLE_ADMIN` via
litemall-svcsecurity:

- `POST /` — define (DRAFT)
- `POST /{id}/activate` — activate
- `POST /{id}/evaluate` — compute audience from real statistics, return + emit
  `PromotionTargetedEvent`
- `GET /list`, `GET /{id}` — read

## Persistence

`litemall_promotion_campaign` (Flyway `V18`, undo `U18`), hand-written
`LitemallPromotionCampaignMapper`, `LitemallPromotionCampaignRepositoryImpl` —
mirroring the Phase-1 combination vertical. Criteria segments and target goods
ids are stored CSV; budget and score floors are nullable columns. The audience
itself is **event + endpoint-response primary**; the campaign row persists
`assigned_count`/`spent_budget` for audit.

## Cross-module follow-up (raise, do not edit)

The RFM statistics come from **litemall-order**, which is out of scope for this
worktree. The promotion service codes against this agreed contract:

```
GET /srv/order/admin/stat/customer-rfm?since=<ISO-8601-local-datetime>
→ ApiResponse<List<{ userId:int, lastOrderAt:datetime, orderCount:int, totalSpend:decimal }>>
```

(authenticated admin / machine-token; one row per customer with order activity
since `since`). **Follow-up for the `order` worktree:** expose this endpoint
(per-customer paid-order count, total amount, and most-recent order time over the
window). Until it exists, `OrderStatisticsAdapter` degrades to an empty
population (logged), so evaluation returns an empty audience rather than failing.
Host is `order.service.url` (profile-overridable; default `:8084` — confirm
against the order service's actual port when wiring).

## Phase 3 seams (not built here)

- `CustomerStatisticsProvider` → add `MatomoStatisticsAdapter` (Reporting API).
- `AudienceSelector` → add an ML implementation (propensity/uplift/LTV).
- `PromotionTargetedEvent` → a Mautic ACL maps it to a campaign/segment trigger.
