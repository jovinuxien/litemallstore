# `/srv` backend follow-ups for the customer SPA

The `litemall-gateway-api` customer SPA was built to **litemall-vue** parity and wired
**exclusively to `/srv`** (never `/wx`) through the api seam in
`src/main/webapp/app/shared/api/*.ts`. Several customer features only have a backend on the
legacy `/wx/**` route today; per the project decision those public/customer endpoints are
being **moved onto `/srv`** in the owning services. This file is the contract the SPA already
codes against. Until each lands, the corresponding view renders a graceful empty/error state
(`isMissingEndpoint` → 404/501) rather than breaking.

Grep the SPA for the open items: `grep -rn "TODO(/srv follow-up" src/main/webapp/app/shared/api`.

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
| `/srv/comment/post` | POST | `ReviewForm` (PDP Reviews CTA + OrderDetail "Unrated") | **Wave-2 goods-management** — request shape is the contract in `docs/handoff-goods-management-engagement.md`; guarded until it ships |

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
here**. Everything below stays guarded until `fix/promotion` merges and the service runs (master's
promotion-service can't boot the coupon verticals — bean-conflict fixed only on that branch).

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

## Owner: litemall-goods-management (engagement verticals) — Wave-2, shapes contract committed
Request/response shapes the SPA already sends: `docs/handoff-goods-management-engagement.md`.
All guarded (`isMissingEndpoint`) until goods-management ships them.

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

## Owner: auth edge (`/auth/**`, gateway-api `AuthController`)
| Endpoint | Verb | Used by | Notes |
|---|---|---|---|
| `/auth/register` | POST | `modules/login/Register` | issue customer JWT like `/auth/login` |
| `/auth/reset` | POST | (reserved) | password reset |
