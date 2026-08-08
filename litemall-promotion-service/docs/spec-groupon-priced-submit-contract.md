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
  `GROUP_COMPLETED {groupPinkId, combinationId, memberCount, memberPinkIds[]}`
  and `GROUP_EXPIRED {...same fields}` — or poll `GET /pink/{pinkId}`.
  Recommended order-side policy (crmeb-style): take payment at submit; if the
  group later fails/expires, refund via order's existing refund path
  (wallet/PSP seam) for the affected orders.
- **`memberPinkIds[]` (Wave 21, ADDITIVE — existing fields unchanged):**
  plain integer slot ids, the leader's own slot included, so order can find
  the affected local orders (each order stores its buyer's `pinkId`).
  - `GROUP_COMPLETED`: every slot completed with the group.
  - `GROUP_EXPIRED`: only the slots failed BY this expiry/dissolution — slots
    released earlier (order cancelled pre-expiry) are NOT listed; their
    orders were already handled at release time.

## Order-linkage follow-ups (IMPLEMENTED — Wave 21)

Both endpoints: machine JWT + forwarded `X-User-Id` (the buyer owning the
slot), standard operation-result envelope `{success, message, operationType,
data}` — HTTP 200 on success, 400 with a typed message on refusal.

### `POST /srv/promotion/combination/pink/{pinkId}/attach-order`

Body: `{"orderId": <int>}` (required). Backfills the slot's `order_id` after
placement. **CAS semantics** (check-then-set inside the transactional service
method; order is the only writer of this field):

- slot `orderId` null → set it; success data
  `{pinkId, orderId, status, attached: true}`.
- slot `orderId` == the passed orderId → **idempotent ok**, `attached: false`.
- slot `orderId` set to a DIFFERENT order → typed conflict
  (`"...already attached to order <id>"`), 400, nothing written.
- unknown pinkId → typed `"Group slot not found"`, 400 (never a 5xx/404 body).
- slot not owned by `X-User-Id` → typed refusal.
- slot already FAILED (group expired / slot released) → typed refusal; a
  Pending or Success slot attaches fine (the group may complete between
  submit-validation and attach).

### `POST /srv/promotion/combination/pink/{pinkId}/release`

Body: `{"orderId": <int>}` — the cancelled order (replay-safe guard, mirror
of the coupon release). Frees the slot ONLY while its group is still
**Pending**. Chosen semantics (what the V30 state machine supports —
documented here as the contract):

- A released slot is flipped to **FAILED** (the state machine's only
  "out of the group" terminal state); the row is KEPT for audit/idempotency.
  Released slots stop counting toward the headcount everywhere (join
  fullness/dedup, `GET /pink/{pinkId}` `memberCount`/`members[]`), so the
  seat becomes joinable again — including by the user who released it.
- **Member release** → success data
  `{pinkId, released: true, groupDissolved: false, memberCount: <active
  slots remaining>}`. No event is emitted (only this order is affected, and
  the caller is order itself).
- **LEADER release dissolves the group**: every still-pending slot fails and
  ONE `GROUP_EXPIRED` event is emitted with the failed slot ids in
  `memberPinkIds[]` — exactly the expiry-sweep signal, so other members'
  paid orders ride order's existing auto-cancel/refund listener. Success
  data `{pinkId, released: true, groupDissolved: true, memberPinkIds[]}`.
- **Idempotent**: unknown pinkId, or a slot already FAILED whose recorded
  orderId matches (or was never attached), returns success with
  `{released: false, alreadyReleased: true}` — covers member-release
  replays AND leader-release replays after dissolution.
- **Too late**: a still-active slot of a settled group — group **Success**
  (completed), or any other settled state — is a typed refusal
  (`"Too late — the group has already completed or failed"`); completed
  groups are handled by the aftersale/refund path, never unwound here.
- slot attached to a DIFFERENT order than the passed one → typed conflict.

### `GET /pink/{pinkId}` clarification (Wave 21)

The top-level fields of the response are the REQUESTED slot's own
(`pinkId, combinationId, headId, userId, orderId, requiredMembers,
memberCount, expireTime, status, members[]`) — order validates the buyer's
`userId`/`orderId`/`status` directly on them, for member slots too.
`memberCount` counts ACTIVE (non-released) slots of the group; released
slots are hidden from `members[]` unless the whole group has failed.

## Postiz gate (Wave 21)

The Wave-20 errno-765 refusal on publishing groupon-category promo pages is
RETIRED: priced submit ships with this wave, so groupon pages compose and
publish like any other (with a group-buy teaser line). 764 (page not active)
and 766 (page source unavailable) are unchanged.
