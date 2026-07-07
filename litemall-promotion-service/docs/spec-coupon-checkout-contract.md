# Handoff spec — coupon validate / redeem / release at checkout

- **Producer:** `litemall-promotion-service` (branch `fix/promotion`, Wave 2)
- **Consumer:** `litemall-order` (its Wave-2 "coupon-at-checkout facade" task)
- **Status:** endpoints LIVE on promotion-service; order-side facade is the
  order worktree's job. Order already carries its own coupon exception/VO
  scaffolding (`LitemallCouponServiceLayer` seam in `LitemallOrderServiceImpl`)
  — point that seam at these endpoints via an ACL facade (Feign or REST),
  never at the DB tables.

All calls are service-to-service: machine JWT (client-credentials from
litemall-authserver) + the customer's forwarded identity in `X-User-Id`.
Promotion enforces ownership from `X-User-Id`; a forged body userId is
impossible because none of these endpoints read identity from the body.

Envelope for mutations: HTTP 200 (success) / 400 (failure) with
`{ "success": bool, "operationType": "...", "message": "...", "data": {...} }`.

## 1. Validate — which coupons apply to this checkout

```
GET /srv/promotion/coupon/usable?amount=<subtotal>&goodsIds=1,2&categoryIds=10,11
Headers: Authorization: Bearer <machine>, X-User-Id: <customer>
→ 200 [ { "couponId", "userCouponId", "name", "discount", "min",
          "type", "goodsType", "status", "startTime", "endTime" }, ... ]
```

- `amount` (required): the order subtotal the coupon must gate on (`min` ≤ amount).
- `goodsIds` / `categoryIds` (optional CSVs): the cart's goods and their
  category ids. Needed to satisfy scoped coupons — `goodsType` CATEGORY matches
  `goodsValue` against `categoryIds`, ARRAY against `goodsIds`; a scoped coupon
  with no matching id is excluded. **The caller supplies the cart facts;
  promotion has no cart access by design.**
- Returned list contains only: held by this user, status USABLE, not expired,
  definition NORMAL, threshold met, scope matched. `userCouponId` is the handle
  the redeem step uses. The `startTime`/`endTime` are the held instance's window.

## 2. Redeem — exactly-once consumption at submit

```
POST /srv/promotion/coupon/user/{userCouponId}/redeem
Body: { "orderId": <order id>, "orderSubtotal": <subtotal> }
→ 200 data: { userCouponId, couponId, orderId, discount }
→ 400 message: ownership / not-usable / expired / threshold reasons
```

Semantics the order side must rely on:
- Single-use: redeem transitions USABLE → USED and stamps `order_id` +
  `used_time`. A second redeem of the same `userCouponId` fails with
  "Coupon is not usable" — this is the exactly-once guarantee. There is no
  idempotency by orderId on redeem: **call redeem once per placement attempt,
  after the order row exists and before taking payment.** A 400 here must
  abort the placement (or re-price it without the coupon — order's choice),
  never leave a paid order assuming a discount that was not granted.
- The discount amount is returned; order applies it to its own pricing. The
  threshold is re-checked server-side against `orderSubtotal`.
- An expired-but-USABLE holding is lazily flipped to EXPIRED and the redeem
  fails — no pre-sweep needed.

## 3. Release — failure compensation

```
POST /srv/promotion/coupon/user/{userCouponId}/release
Body: { "orderId": <the order that consumed it> }
→ 200 data: { userCouponId, couponId [, alreadyReleased: true] }
→ 400 message: not-owned / "Coupon was not redeemed by this order"
```

- Call on any failure after redeem: payment declined, stock reversal, order
  cancel/timeout of an unpaid order that had redeemed a coupon.
- **Idempotent:** releasing a coupon that is already USABLE again returns 200
  with `alreadyReleased: true` — safe to retry on network errors.
- **Replay-safe:** only the exact `orderId` that consumed the coupon may
  release it. If the customer meanwhile re-spent the coupon on another order,
  the stale release fails with 400 instead of un-redeeming the new order's
  coupon.
- Emits `COUPON_RELEASED` (Kafka `litemall.promotion.*`, AFTER_COMMIT) for
  downstream audit.

## Failure-mode summary for the order facade

| Order-side situation                   | Call                          | On 400 |
|----------------------------------------|-------------------------------|--------|
| Building the checkout coupon picker    | `GET /usable`                 | treat as empty list |
| Submit with coupon selected            | `POST /{id}/redeem` in the placement flow | abort placement / re-price without coupon |
| Payment failed / order cancelled after redeem | `POST /{id}/release`   | log + retry later (idempotent); a "not redeemed by this order" 400 means the coupon was legitimately re-spent — stop retrying |
| Promotion-service down at submit       | (circuit-break)               | place WITHOUT the discount or fail placement — never assume the discount |
