# Follow-up → `order` worktree: expose the customer address book at `/srv/address/**`

> **STATUS: RESOLVED (2026-06-27, `fix/order`).** `/srv/address/**` is implemented.
> See **Resolution** at the bottom for what landed and the one remaining gateway-api follow-up.


**Owner of the fix:** `fix/order` worktree (order service owns the customer address surface).
**Raised by:** `fix/gateway-api` worktree, during live checkout verification (2026-06-19).
**Why this doc lives here:** discovered while wiring the customer SPA checkout. Per the
`gateway-api` scope rule we do **not** add order business logic in the SPA — this is the
precise spec for the missing endpoint that blocks end-to-end order placement.

---

## Symptom (verified live, 2026-06-19)

The customer SPA checkout cannot place an order. `POST /srv/order/submit`
(`LitemallPlaceOrderCommand`) **requires `addressId`** — and `LitemallOrderServiceImpl`
already resolves it via `addressRepository.findAddress(userId, addressId)` — but **there is
no REST endpoint to create or list a saved address**, so the SPA can never obtain a valid
`addressId`. A manually-typed address in the checkout form has nowhere to go.

The SPA already codes against these paths (`litemall-gateway-api/.../shared/api/userApi.ts`);
all currently **404** at the order service (no controller):

| Verb | Path | SPA caller | Purpose |
|---|---|---|---|
| GET  | `/srv/address/list` | `Checkout`, `modules/user/AddressList` | the user's address book |
| GET  | `/srv/address/detail?id=` | `AddressEdit` | one address |
| POST | `/srv/address/save` | `Checkout`, `AddressEdit` | create / update (returns the saved `id`) |
| POST | `/srv/address/delete` | `AddressList` | `{ id }` |

The SPA's `IAddress` shape (what `/srv/address/list` should return per item):
`{ id, name, tel, province, city, county, addressDetail, areaCode, postalCode, isDefault }`.

## The work is mostly done — only the REST layer is missing

The address **domain + persistence already exist** on `fix/order` and are wired into order
placement. Only `interfaces/rest` + DTOs are absent.

- `domain/model/agregates/LitemallAddressAggregate` — fields:
  `addressId, userId, name, province, city, county, addressDetail, areaCode, postalCode,
  tel, isDefault, addTime, updateTime, deleted` (maps 1:1 to the SPA `IAddress`).
- `domain/model/repositories/LitemallAddressRepository` (+ `…repositories/impl/LitemallAddressRepositoryImpl`)
  already provides everything a controller needs:
  - `getListAddressesByUserId(userId)` / `findAddresses(userId, name, page, limit, sort, order)`
  - `findAddress(userId, addressId)`
  - `insertAddress(addr)` / `updateAddress(addr)` / `deleteAddress(addressId)`
  - `resetDefaultAddress(userId)` (call before persisting a new default)
- `application/internal/LitemallOrderServiceImpl` already injects the repository and calls
  `findAddress(cmdUserId, cmdAddressId)` at submit — so a saved `addressId` flows straight
  into order placement once the SPA can create one.

## Required `order` changes

1. Add `interfaces/rest/LitemallAddressController` mapped at **`/srv/address`**, mirroring
   `LitemallOrderRestController`/`LitemallWalletRestController`:
   - `GET  /list`        → `getListAddressesByUserId` (or `findAddresses` for paging)
   - `GET  /detail?id=`  → `findAddress`
   - `POST /save`        → `insertAddress`/`updateAddress` (id present ⇒ update); honor
     `isDefault` via `resetDefaultAddress` first; **return the persisted `id`** (the SPA needs
     it to immediately pass `addressId` to `/srv/order/submit`)
   - `POST /delete`      → `deleteAddress`
2. Add `interfaces/dtos/address/**` DTOs converting the aggregate ⇄ the `IAddress` JSON shape
   above (snake/camel as the other `/srv` DTOs do), wrapped in the standard `ApiResponse`/errno envelope.
3. **Identity:** bind the owner from the gateway-injected **`X-User-Id`** header (as order +
   wallet do), never from a request param — every query/insert/delete must scope to that user.
   Do **not** repeat the cart controller's caller-supplied-`userId` pattern (the IDOR risk
   already flagged in `handoff-gateway-api-order-routing.md`).

## Gateway routing — already in place (no gateway change needed)

`litemall-gateway-api` route `customer-order` forwards `Path=/srv/order/**,/srv/cart/**`. Address
will need routing too — but note `/srv/**` already falls through to `lb://litemall-goods-management`,
so add `/srv/address/**` to the order route (`customer-order`) so it reaches `order-service-app`
rather than goods-management. That one-line predicate addition is a **`gateway-api` follow-up**;
flagged here so the two land together.

## Cross-references
- `litemall-order/docs/handoff-gateway-api-order-routing.md` — the order/cart/wallet contract + the cart IDOR note.
- `litemall-gateway-api/docs/SRV-FOLLOWUPS.md` — the SPA-side record of this and the post-order payment-action gap.

---

## Resolution (2026-06-27, `fix/order`)

Implemented as specified. **Note:** the address repository was actually a *stub*
(`getListAddressesByUserId`/`findAddresses`/`deleteAddress`/`resetDefaultAddress` were
no-ops, the `name` column was never mapped, `userId` was mis-mapped from the row id, and
insert never surfaced the generated key) — contrary to "the repository already provides
everything a controller needs." Those gaps were fixed so the endpoint actually works.

What landed:
- `interfaces/rest/LitemallAddressController` mapped at `/srv/address` —
  `GET /list`, `GET /detail?id=`, `POST /save`, `POST /delete`. Owner bound **only** from
  the `X-User-Id` header; **no caller-supplied `userId` param** (no IDOR).
- `interfaces/dtos/address/{AddressDtoResponse,AddressSaveRequest}` ⇄ the `IAddress` shape,
  wrapped in the standard `ApiResponse`/errno envelope (`ApiResponse.ok/fail` helpers added).
- `application/internal/LitemallAddressServiceLayer` — scopes every read/write to the header
  user; `/save` clears the prior default via `resetDefaultAddress` when `isDefault` is set,
  enforces ownership on update/delete, and **returns the persisted `id`**.
- `infrastructure/repositories/impl/LitemallAddressRepositoryImpl` — real implementations:
  list/find by user (live rows only), insert that captures the generated key onto the
  aggregate, logical-delete, reset-default, `name` mapped, `userId` mapping bug fixed.

Verification: `mvn -q -o -pl litemall-order -am compile` clean.

**Remaining gateway-api follow-up (NOT done here):** add `/srv/address/**` to the
`customer-order` route predicate so it reaches `order-service-app` instead of falling through
to goods-management.
