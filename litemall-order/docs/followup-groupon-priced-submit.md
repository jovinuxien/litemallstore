# Follow-up — groupon (combination) priced submit is NOT implemented on order

> **STATUS (2026-08-08, Wave 21): IMPLEMENTED.** `POST /srv/order/submit` accepts
> the optional `pinkId`; placement validates the slot + campaign at promotion,
> prices the line at `combinationPrice`, persists `litemall_order.pink_id` (V56)
> and attach-orders the slot after commit (fail-soft). Stale slots are the typed
> `LitemallInvalidGroupSlotException` 422 reject — never a silent retail
> re-price. Cancel paths release the slot; the `litemall.promotion.group.expired.v1`
> Kafka listener auto-cancels + tender-parity-refunds paid orders of failed groups.
> This note is kept as history of the original gap.

- **Decision:** deferred by the user on 2026-07-10 (Wave 2 scope = coupon-at-checkout
  + aftersale only). This note is the pointer for the next assignment.
- **Contract to consume:**
  `litemall-promotion-service/docs/spec-groupon-priced-submit-contract.md`.

## The gap

Promotion's combination vertical is live (browse/start/join, table
`litemall_combination_pink` V30) and the customer SPA's `/groupon` flow works, but
`POST /srv/order/submit` does not accept the proposed `pinkId` field — a group-buy
checkout today prices at plain retail (observed and noted by the gateway-api
worktree in its `SRV-FOLLOWUPS.md`).

The legacy `litemall_groupon`/`litemall_groupon_rules` path inside placeOrder is a
separate, older flow and stays untouched.

## What the next iteration must do (per the spec)

1. Accept `pinkId` on the submit body (`LitemallPlaceOrderCommand`).
2. Validate via `LitemallPromotionFacade` (extend it — the ACL now exists):
   `GET /srv/promotion/combination/pink/{pinkId}` — buyer owns the slot, status
   Pending/Success, slot's `orderId` null; `GET /srv/promotion/combination/{id}` —
   goods matches, campaign unchanged.
3. Price the line at `combinationPrice` (cap qty at `limitPerUser`), store `pinkId`
   on the order, and ask promotion to add
   `POST /srv/promotion/combination/pink/{pinkId}/attach-order {orderId}` (offered
   in the spec, not yet implemented) for slot↔order linkage.
4. Policy on group failure/expiry (crmeb-style, recommended by the spec): keep
   pay-at-submit; refund affected orders via the tender-parity refund path when
   `GROUP_EXPIRED` arrives (Kafka `litemall.promotion.*`) or on poll.
