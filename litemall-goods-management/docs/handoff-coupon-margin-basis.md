# Handoff spec — coupon margin-guard basis (Wave 18)

- **Producer:** `litemall-goods-management` (branch `fix/goods-management`; endpoint
  built + unit-tested 2026-08-06)
- **Consumer:** `litemall-promotion-service` (Wave-18 coupon margin guard at coupon
  create/update — and later the Phase-2 coupon candidate scorer)
- **Status:** FROZEN. Code to this document, not to the goods-management branch.

Promotion must hard-reject any coupon whose worst-case basket would sell below
`cost × floor` (floor: `litemall.promotion.coupon.margin-floor`, env
`LITEMALL_PROMOTION_COUPON_MARGIN_FLOOR`, default **1.05**, user decision
2026-08-06: hard block, no admin override). This endpoint supplies the basis
numbers; the guard formula and floor live in promotion.

## Endpoint

```
POST /srv/private/admin/insight/margin-basis
Headers: Authorization: Bearer <machine token>
         X-User-Id: <acting admin id, or 0 sentinel>   // REQUIRED — svcsecurity only
                                                       // honours X-User-Roles when
                                                       // X-User-Id is present
         X-User-Roles: ROLE_ADMIN
Body:    { "goodsIds": [1,2,3], "categoryIds": [1036007] }   // either, both, or {}
→ 200 errno envelope, data:
  { "onSaleCount": 1300, "costedCount": 1180, "uncostedCount": 120,
    "maxCostRatio": 0.8000, "minRetailPrice": 4.99 }
```

- Auth is the established sister-service recipe for admin-prefixed paths
  (litemall-order precedent): machine JWT from litemall-authserver +
  `X-User-Roles: ROLE_ADMIN` forwarded alongside it. svcsecurity honours the
  header only behind a valid machine token; the customer edge overwrites
  identity headers, so captured costs are unreachable from any customer path.
- `categoryIds` accepts ANY tree level (L1 roots included) — goods-management
  expands each id to its whole subtree **at query time**, so an L1-scoped
  coupon automatically covers leaf categories created by future CJ syncs.
  Store the admin's L1 picks on the coupon; do NOT pre-expand to leaves.
- Scope semantics match `matchesGoods`: goods ids OR category subtree (union
  when both given). **Both empty/absent = the whole on-sale catalog** — use
  this for ALL-scope (`goodsType 0`) coupons.
- Caps: ≤ 500 `goodsIds`, ≤ 50 `categoryIds`; beyond either → errno 402
  (bad argument). Null/≤0/duplicate ids are dropped silently.
- Only `deleted = 0 AND is_on_sale = 1` goods count — coupons apply to buyable
  goods (viewable-unbuyable rule: off-sale goods can't enter a cart anyway).

## Response semantics

| Field | Meaning |
|---|---|
| `onSaleCount` | on-sale goods in scope. `0` ⇒ the scope currently matches nothing (promotion should reject the coupon as unusable-by-construction). |
| `costedCount` | of those, goods with a captured CJ cost (`cost > 0`, Wave-12 capture). |
| `uncostedCount` | goods whose cost is not yet captured (rotation covers them over time). Margin discipline: these are EXCLUDED from `maxCostRatio`, never treated as ratio 0. |
| `maxCostRatio` | max of `cost / retail_price` over the costed in-scope goods, 4 dp — the WORST margin in scope. **`null` (never 0) when `costedCount = 0`.** |
| `minRetailPrice` | cheapest in-scope on-sale retail. `null` when scope is empty. Sanity input for `min`-spend plausibility if wanted. |

## Recommended guard math (promotion-side)

Worst case = a basket at exactly the `min` threshold composed of the
worst-ratio in-scope goods. With `r = maxCostRatio`, `f = floor`:

- **Flat coupon** (`discount` = D, `min` = M): require `M − D ≥ M × r × f`
  ⇒ `D ≤ M × (1 − r × f)`. Reject otherwise; the errno message must state the
  computed maximum D so the admin can adjust.
- **Percent coupon** (rate p, cap C): require `p ≤ (1 − r × f) × 100`; the cap
  C additionally bounds absolute exposure but is NOT a substitute for the rate
  check (a capped-but-over-rate coupon still loses money on small baskets).
- At standard 1.25 pricing (r = 0.8, f = 1.05): max ≈ 16% — e.g. up to ~$8 off
  a $50-min coupon. Per-category margin overrides (Wave 14) flow in
  automatically because they reprice `retail_price`, which moves `r`.
- `maxCostRatio = null` (nothing costed yet) ⇒ the guard cannot certify
  profitability ⇒ **reject** with a typed "cost not yet captured for this
  scope" errno (honest degrade; retry after the nightly rotation).
- `uncostedCount > 0` with a usable ratio ⇒ proceed on the costed basis but
  include `uncostedCount` in the create response as a non-blocking warning.
- Re-run the guard on UPDATE as well as create (scope/discount/min edits), and
  cache nothing — the basis is one cheap aggregate query.

## Operational notes

- One indexed aggregate over `litemall_goods`; fine to call synchronously on
  every coupon create/update. Not a request-path/customer endpoint — do not
  call it per checkout.
- goods-management down ⇒ promotion must fail CLOSED (typed "guard
  unavailable, try again" errno — never save an unguarded coupon).
- Anchors (producer side): `AdminInsightController.marginBasis`,
  `MarginBasisService`, `InsightMapper.selectMarginBasis` (+ XML). Unit tests:
  `MarginBasisServiceTest` (4/4).
