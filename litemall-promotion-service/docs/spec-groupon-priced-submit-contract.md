# Handoff spec — group-buy (combination) priced submit

- **Producer:** `litemall-promotion-service` (Wave 2 — promotion owns
  combination participation, table `litemall_combination_pink` V30; see the
  ADR addendum in `adr-combination-groupon-split.md`)
- **Consumer:** `litemall-order` (submit-path pricing)
- **Scope note:** order's legacy `litemall_groupon`/`litemall_groupon_rules`
  flow is untouched; this contract covers orders placed against the NEW
  combination campaigns.

Auth: machine JWT + forwarded `X-User-Id`, as everywhere.

## Customer flow that produces the submit

1. Browse: `GET /srv/promotion/combination/active` (or legacy
   `GET /srv/groupon/list`) → campaign `{combinationId, goodsId,
   combinationPrice, originalPrice, requiredMembers, startTime, endTime}`.
2. Start: `POST /srv/promotion/combination/{combinationId}/start` → data
   `{pinkId, combinationId, requiredMembers, expireTime}` — the caller is the
   group leader. Or join: `POST /srv/promotion/combination/pink/{leaderPinkId}/join`
   → data `{pinkId, groupPinkId, memberCount, requiredMembers, completed}`.
3. The SPA then submits an order for the campaign's goods carrying the buyer's
   **own slot id** (`pinkId`) — proposed submit-body field: `"pinkId": <int>`.

## What order must do at submit (validate + price)

1. `GET /srv/promotion/combination/pink/{pinkId}` →
   `{pinkId, combinationId, headId, userId, orderId, requiredMembers,
     memberCount, expireTime, status, members[]}` (404 if unknown).
   Validate: `userId` == the buyer (from `X-User-Id`); `status` is `Pending`
   or `Success` (a `Failed`/expired slot prices at normal retail — reject or
   re-price, order's choice); slot `orderId` is null (one order per slot).
2. `GET /srv/promotion/combination/{combinationId}` → the campaign. Validate
   the submitted goodsId matches `combination.goodsId` and the campaign is
   still the same offer; **price the line at `combinationPrice`** (unit price),
   quantity capped by `limitPerUser` when set.
3. Place the order with that price. Store `pinkId` on the order record
   (order-side column/attr — order worktree's schema call).

## Group success / failure semantics

- A group completes the moment `memberCount` reaches `requiredMembers`
  (promotion flips every slot to `Success` and emits `GROUP_COMPLETED`).
- A pending group past `expireTime` is failed by promotion's scheduled sweep
  (every `litemall.promotion.groupon.sweep-fixed-delay-ms`, default 60 s),
  emitting `GROUP_EXPIRED`.
- **Signals order can consume:** Kafka events on the
  `litemall.promotion.<event>.v1` dynamic destinations —
  `GROUP_COMPLETED {groupPinkId, combinationId, memberCount}` and
  `GROUP_EXPIRED {...}` — or poll `GET /pink/{pinkId}`. Recommended order-side
  policy (crmeb-style): take payment at submit; if the group later fails/
  expires, refund via order's existing refund path (wallet/PSP seam) for the
  affected orders.

## Follow-ups promotion will add on request (not yet implemented)

- `POST /srv/promotion/combination/pink/{pinkId}/attach-order {orderId}` —
  backfill the slot's `order_id` after placement so promotion-side monitoring
  shows order linkage (column exists; endpoint pending order's need).
- Slot-release on order-cancel before group completion (mirror of the coupon
  release), if order wants a cancelled order to free the group slot.
