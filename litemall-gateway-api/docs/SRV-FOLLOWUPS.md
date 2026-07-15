# `/srv` backend follow-ups for the customer SPA

The `litemall-gateway-api` customer SPA was built to **litemall-vue** parity and wired
**exclusively to `/srv`** (never `/wx`) through the api seam in
`src/main/webapp/app/shared/api/*.ts`. The legacy `/wx` services are now **deleted**
(Wave-4 decommission, `a782082fd`) and every endpoint the SPA consumes is live on `/srv` —
see the Wave-4 close-out at the bottom. The tables below are kept as the contract history;
the only still-open item is the Stripe CARD stub (tabled under "Discovered during the
order/user redesign").

## Owner: litemall-goods-management (`/srv/**` catch-all) — public catalog/content
| Endpoint | Verb | Used by | Notes |
|---|---|---|---|
| `/srv/brand/list` | GET | `modules/brand/BrandList` | ✅ **LANDED** — guard removed (2026-07-07) |
| `/srv/brand/detail?id=` | GET | `modules/brand/BrandDetail` | ✅ **LANDED** |
| `/srv/topic/list` | GET | `modules/topic/TopicList` | ✅ **LANDED** — guard removed |
| `/srv/topic/detail?id=` | GET | `modules/topic/TopicDetail` | ✅ **LANDED** — guard removed |
| `/srv/topic/related?id=` | GET | (reserved) | related topics |
| `/srv/comment/count?valueId=&type=0` | GET | `modules/product/.../Reviews` | ✅ **LANDED** |
| `/srv/comment/list?valueId=&type=0` | GET | `modules/product/.../Reviews` | ✅ **LANDED** — guard removed |
| `/srv/comment/post` | POST | `ReviewForm` (PDP Reviews CTA + OrderDetail "Unrated") | ✅ **LANDED** (goods-management `ab3d365e6`, 2026-07-07) — guard removed 2026-07-10 |

> `/srv/goods/list`, `/srv/goods/detail`, `/srv/goods/related`, `/srv/catalog/*`, `/srv/search`,
> `/srv/suggest` already exist and are consumed as-is.

## Owner: litemall-order (`/srv/order/**`, `/srv/cart/**`)
| Endpoint | Verb | Used by | Notes |
|---|---|---|---|
| `/srv/order/list?showType=&page=&limit=` | GET | `modules/order/OrderList`, `RefundList` | `{ list: IOrderListItem[], total }` |
| `/srv/order/detail?orderId=` | GET | `modules/order/OrderDetail` | `IOrderDetail` |
| `/srv/order/{id}/actions/cancel` | POST | OrderList | exists (cancel) |
| `/srv/order/{id}/actions/confirm` | POST | OrderList | confirm receipt |
| `/srv/order/{id}/actions/refund` | POST | OrderList | refund request |
| `/srv/order/{id}/actions/delete` | POST | (reserved) | delete order |
| `/srv/order/{id}/actions/pay` | POST | `Checkout` payment step (`orderSlice.payOrder`) | ✅ **LANDED** — pay a placed order. Body `PaymentActionRequest { paymentMethod, paymentIntentId }`; CARD → `CREDIT_CARD` (+ Stripe intent), WALLET → wallet debit. Returns `OrderOperationDtoResponse` (200 ok / **402** insufficient balance, order left unpaid). |
| `/srv/address/list` | GET | `Checkout`, `modules/user/AddressList` | ✅ **LANDED** — address book, scoped by `X-User-Id` |
| `/srv/address/detail?id=` | GET | `AddressEdit` | ✅ **LANDED** |
| `/srv/address/save` | POST | `Checkout`, `AddressEdit` | ✅ **LANDED** — returns the new `addressId`; `Checkout` saves a typed address then submits with it |
| `/srv/address/delete` | POST | `AddressList` | ✅ **LANDED** |

> **Update (2026-06-27) — the two checkout blockers have landed; Task C is now wired e2e:**
> - `LitemallAddressController` (`/srv/address/{list,detail,save,delete}`, order's `ApiResponse`
>   envelope) and `POST /srv/order/{id}/actions/pay` (`PaymentActionRequest`) now exist in the
>   order service. The gateway `customer-order` route predicate was extended to claim
>   **`/srv/address/**`** (it previously fell through to the goods catch-all → 404).
> - Checkout is now a **two-step** place→pay flow: `POST /srv/order/submit` (creates the order,
>   201) then `POST /srv/order/{id}/actions/pay` (charges it). Both return a bare
>   **`OrderOperationDtoResponse`** with the outcome on the HTTP status (201 created / 422 stock /
>   200 paid / 402 insufficient balance) — **not** an `{errno,errmsg,data}` envelope. `orderSlice`
>   parses this shape; a payment retry pays the same placed order (no duplicate).
> - CARD payment uses a **stub** Stripe PaymentIntent id (`pi_stub_<orderId>`) until real Stripe
>   Elements/keys are wired — tracked below.
>
> **Earlier verification (2026-06-19) — still current:**
> - The cart REST verbs are mounted under **`/srv/cart/items`**, NOT bare `/srv/cart`.
> - `POST /srv/order/submit` takes a **`LitemallPlaceOrderCommand`**
>   `{ cartId, addressId, couponId, userCouponId, message, grouponRulesId, grouponLinkId }`
>   with the buyer bound from `X-User-Id` — no inline `items`/`shipping`/`paymentMethod`.
> - Runtime note: `order-service-app` (:8085) bring-up/stability is owned by the `order` worktree
>   (run-from-MAIN rule).
>
> **Remaining follow-up (gateway-api):** real Stripe Elements card capture (publishable key +
> `@stripe/react-stripe-js` + a client-confirmed PaymentIntent) to replace the CARD stub.

## Owner: litemall-promotion-service (coupons + group-buy) — routing DECIDED (2026-07-07)
Contract: `litemall-promotion-service/docs/spec-gateway-routes.md` (user-approved 2026-07-07,
committed on `fix/promotion`). The SPA **keeps its legacy paths**; promotion serves them natively
via `interfaces/rest/legacy/**`. The gateway `customer-promotion` route
(`/srv/promotion/**,/srv/coupon/**,/srv/groupon/**` → `lb://promotion-service-app`) is **landed
here**. ✅ `fix/promotion` **merged to master** (`73f12660c`, 2026-07-07) — every endpoint below is
live; the SPA guards were removed 2026-07-10.

| Endpoint | Verb | Used by | Notes |
|---|---|---|---|
| `/srv/coupon/list` | GET | `CouponStrip` | claimable coupons, errno envelope |
| `/srv/coupon/mylist?status=` | GET | `modules/user/Coupons` | status 0/1/2 = SPA tab index; item `id`=userCouponId, `cid`=couponId |
| `/srv/coupon/selectlist?amount=&goodsIds=&categoryIds=` | GET | `Checkout` usable-coupon picker | **params changed** from `cartId` — caller passes the cart facts (done 2026-07-07) |
| `/srv/coupon/receive` | POST | `CouponStrip` | `{couponId}` |
| `/srv/coupon/exchange` | POST | (api ready, no UI yet) | `{code}` redemption-code exchange |
| `/srv/groupon/list` | GET | `modules/groupon/Groupon` | legacy GrouponItem shape |
| `/srv/promotion/combination/{active,{id},{id}/start,pink/{id}/join,my,pink/{id}}` | GET/POST | `Groupon` (via `promotionApi`) | canonical surface; bare DTOs, mutations 200/400 `{success,message,…}` |

> **Cross-service caveats (not gateway-api work):**
> - **Coupon-at-submit bridge**: order-service today validates/redeems `couponId`/`userCouponId`
>   against its OWN coupon tables; promotion's issuance lands in promotion's tables. Until the
>   order worktree points its `LitemallCouponServiceLayer` seam at promotion's
>   validate/redeem/release endpoints (spec: `litemall-promotion-service/docs/`
>   `spec-coupon-checkout-contract.md`), a coupon claimed via promotion is rejected at submit.
>   **Live-observed 2026-07-07:** submit with a promotion-issued pair (`couponId:9`,
>   `userCouponId:3`, usable per `selectlist`) → HTTP 200 `{errno:502,"System internal error"}`;
>   the identical submit without the coupon → 201, order created. Order-worktree follow-up: the
>   facade per the spec, and until then a clean **422 with a coupon-naming message** instead of
>   the generic 502 (the SPA's inline remove-coupon-and-retry affordance keys on 422/"coupon";
>   a 502 falls back to the page-level alert).
> - **Group-price ordering**: order submit does not yet accept the proposed `pinkId` field
>   (`spec-groupon-priced-submit-contract.md`) — starting/joining groups works, but checkout still
>   prices at retail until the order worktree lands it.

## Owner: litemall-goods-management (engagement verticals) — ✅ LANDED (`ab3d365e6`, 2026-07-07)
Request/response shapes contract: `docs/handoff-goods-management-engagement.md`. All endpoints
below are live on master; the SPA guards were removed 2026-07-10.

| Endpoint | Verb | Used by |
|---|---|---|
| `/srv/collect/list?type=0` | GET | `modules/user/Favorites` |
| `/srv/collect/addordelete` | POST | `Favorites`, product `CollectButton` |
| `/srv/footprint/list` | GET | `modules/user/Footprint` |
| `/srv/footprint/record` | POST | product `Detail` (fire-and-forget on view, added 2026-07-07) |
| `/srv/footprint/delete` | POST | (api ready) |
| `/srv/feedback/submit` | POST | `modules/user/Feedback` |
| `/srv/comment/post` | POST | `ReviewForm` |

## Owner: a customer/user concern (no service yet) — routing decision needed
| Endpoint | Verb | Used by |
|---|---|---|
| `/srv/user/index` | GET | `modules/user/UserCenter`, `Profile` |
| `/srv/user/profile` | POST | `Profile` |

## Discovered during the order/user redesign (2026-06-27)
- **Customer session is not rehydrated on reload (gateway-api SPA bug).** ✅ **FIXED (2026-07-04).**
  `auth/customerAuthSlice` now seeds `token`/`refreshToken`/`userInfo`/`isAuthenticated` from
  sessionStorage at slice init (`restoreFromStorage()`), persists `customerUserInfo` on
  login/register, and clears all three keys on logout. `config/axiosinstance` additionally
  auto-expires the session (clear storage + redirect `/login`) on HTTP **401** or a litemall
  **`errno:501`** envelope.
- **Order service answers a bad/missing JWT with HTTP 200 `{errno:502,"System internal error"}`**
  (observed on `/srv/order/list`, `/srv/cart/index`, `/srv/address/list`, 2026-07-04) instead of
  **401** or the canonical litemall `errno:501` "please login". The SPA's session-expiry hook keys
  on 401/501, so an expired token currently surfaces as a generic error rather than a clean bounce
  to `/login`. **Follow-up for the `order` worktree:** map auth failures (missing/invalid
  `X-User-Id` / token) to 401 or `errno:501` on the customer `/srv/order|cart|address` paths.
- **CARD payment is a stub.** Checkout/Payment send a placeholder `pi_stub_<orderId>` PaymentIntent
  id; real Stripe Elements (publishable key + `@stripe/react-stripe-js` + client-confirmed intent)
  is still pending (gateway-api). WALLET is fully functional.
- The redesigned order list/detail, coupons, and feedback views render graceful empty states until
  the `/srv/order/list|detail`, `/srv/coupon/mylist`, and `/srv/feedback/submit` endpoints land
  (order / promotion / user follow-ups already tabled above).

## Wave-2 SPA wiring (2026-07-07)
- Gateway routes added: `customer-promotion` (`/srv/promotion/**,/srv/coupon/**,/srv/groupon/**`
  → `lb://promotion-service-app`) and the explicit `customer-engagement`
  (`/srv/collect|footprint|feedback|comment/**` → `lb://litemall-goods-management`), both before
  the goods catch-all. Machine-token relay + `X-User-*` identity forwarding fire on all `lb://`
  routes (GlobalFilter/WebFilter — no per-route wiring).
- Coupon-at-checkout wired: usable-coupon picker from `selectlist` (cart facts as params), submit
  sends both `couponId` and `userCouponId`, a coupon-shaped 422 shows inline at the picker with
  remove-and-retry, and the order confirmation now fetches `/srv/order/detail` to render the
  goods/shipping/coupon/actual breakdown.
- Groupon page: browse from legacy `/srv/groupon/list`; start/join/my-groups on the canonical
  combination surface (guarded).
- Review submission (`ReviewForm` → `POST /srv/comment/post`) and footprint record-on-view built
  ahead, guarded.
- **Guard audit**: `isMissingEndpoint` removed everywhere the endpoint is live (cart, order
  list/detail, address book, brand, topic, goods listing, comment list). Guards remain ONLY for:
  coupon/groupon (until `fix/promotion` merges + service runs), collect/footprint/feedback/
  comment-post (goods-management Wave-2), `/srv/user/index|profile` (no owning service yet).

## Wave-2 close-out (2026-07-10)
- Both blocking backend merges landed on master 2026-07-07: goods-management engagement verticals
  (`ab3d365e6`) and promotion coupon/group-buy verticals (`73f12660c`). Master merged into this
  branch; the now-stale guards were removed from: `Favorites`, `Footprint`, `Feedback`, `Coupons`,
  `Groupon` (incl. dropping its `actionsAvailable` state), `CollectButton`, `CouponStrip`,
  `ReviewForm`.
- **The ONLY remaining `isMissingEndpoint` guards** are `UserCenter`/`Profile`
  (`/srv/user/index|profile` — genuinely no owning service yet, tabled above).
- Still blocked on the `order` worktree (acceptance bullets that cannot pass yet):
  **coupon honored at submit** (promotion-issued coupon → order 200 `{errno:502}`; needs the
  `LitemallCouponServiceLayer` → promotion facade per `spec-coupon-checkout-contract.md`, with a
  clean 422 meanwhile) and **group-priced ordering** (`pinkId` submit field per
  `spec-groupon-priced-submit-contract.md`). Aftersale REST surface also not started on order
  (only value-object scaffolding) — the `/srv/order/**` route already covers it whenever it lands.
- SPA-side mitigation shipped for the first bullet: `Checkout.tsx` now shows the inline
  remove-coupon-and-retry affordance on ANY rejected submit that carried a coupon (except the
  slice's own 400/401 pre-checks), because order's generic "System internal error" never matches
  the old `/coupon/i` message test. The page-level alert still shows the underlying error.
- Live e2e re-run 2026-07-10 through `:9001` → `:8090`: collect toggle/list, footprint
  record/list + same-day dedupe, feedback submit → admin list, comment post → list, coupon
  claim → mylist → selectlist include/exclude by amount, group-buy rule browse → start →
  second-user join → `Success` — all PASS. Cart add/update could not be re-verified that day
  (the shared dev `:8082` was running the goods-management worktree's in-flight build, which
  broke order's cart-add facade validation with `errno:402`; stock numbers were being mutated
  under live test) — plain-checkout regression stands on the 2026-07-07 verification; re-check
  after the goods-management branch merges.

## Owner: auth edge (`/auth/**`, gateway-api `AuthController`)
| Endpoint | Verb | Used by | Notes |
|---|---|---|---|
| `/auth/register` | POST | `modules/login/Register` | issue customer JWT like `/auth/login` |
| `/auth/reset` | POST | (reserved) | password reset |

## Wave 4 (2026-07-13) — remaining guards + dependencies

- `/auth/register|reset|reset/request|reset/confirm|me|profile` are LIVE (this
  wave, `docs/handoff-auth-account.md`) — the auth-edge table above is closed.
  `/srv/user/index|profile` stubs are deleted (replaced by `/auth/me|profile`).
- **Guards remaining, each waiting on a backend merge:**
  - `ImageUploader` (Profile avatar, ReviewForm, Feedback, AftersalePanel
    photos) → `POST /srv/storage/upload` — goods-management Wave-4 Task A.2,
    committed on `fix/goods-management`, unmerged. URL-text fallback until then.
  - Pickup checkout toggle → `GET /srv/store/list` — order Wave-4 stores task,
    spec NOT yet committed; the SPA ships an ASSUMED contract (see
    `IStore` / `PlaceOrderParams` comments) and hides pickup until the endpoint
    answers. RE-CHECK field names when `litemall-order/docs/` lands the spec.
  - DIY home / articles / region cascade degrade to legacy/friendly-empty while
    goods-management's content endpoints (committed, unmerged) 404.
- **Freight-quote compat note:** the PRE-template order service 402s
  ("Parameter value is wrong", strict deserialization) when the quote body
  carries the Wave-4 `addressId`/`items` fields — verified live 2026-07-13.
  `Checkout.tsx` falls back to the legacy `{subtotal}` body on any enriched-
  quote failure, so either deploy order works. Remove the fallback once the
  freight-template order service is the only one deployed.
- Live-verified this wave through `:8090` (single gateway process, ephemeral
  JWT): register→login→order-list/wallet as the SECOND user; full password
  change + email-reset flows; tracking not-shipped payload + owner-scoping
  (foreign order → errno 404 → panel hides); aftersale apply(+pictures) → 730
  double-apply guard → cancel on a PAID order.

## Wave-4 close-out (2026-07-15) — residuals cleared

All Wave-4 backends merged 2026-07-13 (`286b9f070` this SPA, `f54904059` order,
`d219c88ac` goods-management) and the legacy wx/admin APIs were deleted
(`a782082fd`); master's `403ff3455` already dropped the storage/content guards.
This pass cleared the three recorded residuals:

- **Freight-quote legacy-body fallback removed** (`Checkout.tsx`) — the
  pre-template order service it protected against no longer exists; the
  enriched `{subtotal, addressId, items}` body is the only quote body.
- **Pickup contract reconciled** against the committed
  `litemall-order/docs/handoff-gateway-api-pickup.md` (it landed after the SPA
  shipped an ASSUMED shape). Real drift fixed: order detail sends
  `storeId`/`verifyTime` — never an embedded `pickupStore` object or
  `pickupName`/`pickupMobile` — so the store card on `OrderDetail` was dead.
  It now fetches `GET /srv/store/detail?id=` (new `orderApi.storeDetail`),
  renders `address + detailedAddress`, and shows a "Collected" state once
  `verifyTime` is set (replacing the code/QR block). `IStore` gained
  `intro`/`detailedAddress`/`logo`. Live-observed: `verifyTime` serialises as
  a LocalDateTime tuple (`[y,m,d,h,min,s]`, the known Jackson shape) —
  formatted like the review dates, not rendered raw.
- **`isMissingEndpoint` retired entirely** — the last two guards
  (`AftersalePanel`, `TrackingPanel`) protected now-live endpoints. Aftersale
  list failures degrade to an empty list; the tracking panel hides only on
  errno 404 (foreign/unknown order). The helper is deleted from
  `shared/api/http.ts` — nothing consumes it.
