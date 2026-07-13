# ADR — Freight templates (Wave 4, Task A)

Status: accepted · 2026-07-13 · owner: `litemall-order`

## Decision

One authority — `application/internal/FreightCalculationService` — prices freight for
BOTH `POST /srv/order/freight-quote` and the transactional submit path, so quote and
submit can never disagree. It reuses the schema that already existed: V8's
`litemall_shipping_templates{,_region,_free}` and V2's `litemall_goods.temp_id`
(0 = unbound). V34 adds `country_code varchar(4) DEFAULT '*'` + `province_name
varchar(63)` to the region/free rows (international, free-text-address matching)
and `is_default` to the template header.

## Resolution ladder (outermost first)

1. `freight.template.enabled=false` (CODE DEFAULT — dev yml turns it on) or a
   CJ-fulfilled cart → legacy flat rule (`litemall_express_freight_min/value`),
   byte-identical to pre-Wave-4. The informational CJ logistics quote block is
   untouched.
2. Global free minimum: subtotal ≥ `litemall_express_freight_min` → freight 0,
   `source: FREE_MIN` (pre-Wave-4 regression promise).
3. Template groups: lines group by goods `temp_id`; 0 → the `is_default` template
   when set, else the flat bucket. Per group: free rules first (when `appoint >= 1`),
   then the crmeb first/continue formula over the most specific region row:
   `(country, province)` → `(country, NULL)` → `('*')`. Province matching is
   case-insensitive/trimmed against `litemall_address.province` free text — the
   Chinese `litemall_region` tree is address-decoupled and NOT used.
   Formula: `firstPrice + ceil((units - first) / continueP) * continuePrice`
   (`RoundingMode.UP`; `units <= first` → `firstPrice`).
4. Flat fallback: the unbound bucket (and any failure) prices at
   `litemall_express_freight_value`, `source: SYSTEM_FLAT`.

Groups combine per `freight.template.combine-mode`: **`max` (default)** — a
multi-template cart pays its worst group, never more; `sum` is the crmeb-exact
alternative (documented as silently raising multi-template carts). The quote endpoint
NEVER 5xxs for freight reasons — any resolution failure degrades to the flat rule
with a WARN.

## Semantics chosen (and why)

- **`appoint` (template header):** `0` = no free rules; `>= 1` = free rules active.
  The V8 column is a tinyint documented "是否有指定包邮条件"; the plan reserves values
  0/1/2 but does not distinguish 1 vs 2, so this wave treats them identically
  (acceptance "appoint=2 free rule honored" holds). A later wave may give 2 a
  narrower meaning (e.g. crmeb's designated-no-delivery).
- **Free rules, OR semantics:** a region-matching free row makes the group free when
  `units >= number` OR `group amount >= price`; a threshold of 0 means "not used"
  (V8 defaults are 0.00 — otherwise every template would ship free). crmeb requires
  both; OR is the plan's choice.
- **DOCUMENTED DEVIATION — unmatched region flat-charges:** crmeb ships a group free
  when no region row matches the destination (its auto-created 全国 row usually hides
  this). We charge the legacy flat value instead: an incompletely-configured template
  must never give away shipping. The breakdown entry says so explicitly.
- **Piece-only v1 (plan §4.4):** `litemall_goods.weight/volume` are now mapped in
  litemall-db (they existed since V2, unmapped) but billing types 2/3 are charged per
  piece with a breakdown note. By-weight/volume billing is deferred.
- **Explicitly ignored:** `litemall_goods.is_postage`-style per-goods free flags (not
  in this schema), and the `temp_id`-like columns on `litemall_seckill`/`litemall_bargain`
  (V9/V10 promo tables) — promo verticals keep their own pricing; freight templates
  bind through `litemall_goods.temp_id` ONLY.
- **`batchGetGoods` hoist NOT taken (deviation from the task line):** the goods facade's
  cross-service JSON carries no freight fields (goods-management's domain has no
  temp_id/weight), so freight reads `temp_id` (+ retail-price fallback) in ONE lean
  litemall-db batch select instead. Hoisting the facade fetch would have added a Feign
  round-trip to every couponless submit for data it cannot deliver; the coupon branch
  keeps its facade fetch for category scope. If goods-management's Wave-4 "freight
  tempId surface" later exposes the binding over the facade, the read can move without
  contract changes (it is private to `FreightCalculationService`).
- **Free-rule amounts:** submit uses cart unit prices (variant truth); the quote uses
  the SPA-supplied `items[].price` when present, else the goods retail price. Edge:
  a variant-priced cart quoted without `price` can differ on free-rule *amount*
  thresholds only — send prices (the handoff says so).

## Caching / invalidation

Template bundles (header + regions + free rules) and the default-template id are
Caffeine-cached 5 minutes; every admin mutation (`create/update/delete/set-default`)
invalidates. Region matching runs in-memory per quote.

## Surfaces

- Customer: `POST /srv/order/freight-quote` — gains optional `addressId`
  (owner-scoped, errno 605 on a foreign/unknown id or an anonymous call carrying one;
  `X-User-Id` itself is optional) and `items[]`; response gains `source` +
  `breakdown[]`. See `handoff-gateway-api-freight-quote.md`.
- Admin: `/srv/private/admin/freight/**` — list/detail/create/update/delete
  (422 while referenced by live goods)/set-default/selectlist (BARE array)/preview.
  See `handoff-gateway-admin-freight.md`.
- Goods binding contract: `handoff-goods-tempid-binding.md`.
