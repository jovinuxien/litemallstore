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
| `/srv/brand/list` | GET | `modules/brand/BrandList` | page/limit/sort/order |
| `/srv/brand/detail?id=` | GET | `modules/brand/BrandDetail` | brand meta |
| `/srv/topic/list` | GET | `modules/topic/TopicList` | page/limit |
| `/srv/topic/detail?id=` | GET | `modules/topic/TopicDetail` | `{ topic, goods[] }` |
| `/srv/topic/related?id=` | GET | (reserved) | related topics |
| `/srv/comment/count?valueId=&type=0` | GET | `modules/product/.../Reviews` | product reviews count |
| `/srv/comment/list?valueId=&type=0` | GET | `modules/product/.../Reviews` | `{ data: IComment[], count }` |
| `/srv/comment/post` | POST | (reserved) | submit a review |

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

## Owner: order / promotion (coupons) — routing decision needed
| Endpoint | Verb | Used by |
|---|---|---|
| `/srv/coupon/list` | GET | `modules/product/.../CouponStrip` |
| `/srv/coupon/mylist?status=` | GET | `modules/user/Coupons`, `Checkout` |
| `/srv/coupon/selectlist?cartId=` | GET | (reserved, checkout) |
| `/srv/coupon/receive` | POST | `CouponStrip` |
| `/srv/groupon/list` | GET | `modules/groupon/Groupon` |

## Owner: a customer/user concern (no service yet) — routing decision needed
| Endpoint | Verb | Used by |
|---|---|---|
| `/srv/user/index` | GET | `modules/user/UserCenter`, `Profile` |
| `/srv/user/profile` | POST | `Profile` |
| `/srv/collect/list?type=0` | GET | `modules/user/Favorites` |
| `/srv/collect/addordelete` | POST | `Favorites`, product `CollectButton` |
| `/srv/footprint/list` | GET | `modules/user/Footprint` |
| `/srv/footprint/delete` | POST | (reserved) |
| `/srv/feedback/submit` | POST | `modules/user/Feedback` |

## Discovered during the order/user redesign (2026-06-27)
- **Customer session is not rehydrated on reload (gateway-api SPA bug).** `auth/customerAuthSlice`
  initialises `isAuthenticated:false` and only flips it on a live login; the stored `customerToken`
  in sessionStorage is ignored at store-creation. So any full page reload of a gated route
  (`/checkout`, `/orders`, `/user/**`) bounces the customer to `/login` even though their token is
  still valid. Fix: seed `isAuthenticated` (and userInfo) from the stored token on slice init / an
  app-bootstrap rehydrate thunk. In-scope for gateway-api; not yet done.
- **CARD payment is a stub.** Checkout/Payment send a placeholder `pi_stub_<orderId>` PaymentIntent
  id; real Stripe Elements (publishable key + `@stripe/react-stripe-js` + client-confirmed intent)
  is still pending (gateway-api). WALLET is fully functional.
- The redesigned order list/detail, coupons, and feedback views render graceful empty states until
  the `/srv/order/list|detail`, `/srv/coupon/mylist`, and `/srv/feedback/submit` endpoints land
  (order / promotion / user follow-ups already tabled above).

## Owner: auth edge (`/auth/**`, gateway-api `AuthController`)
| Endpoint | Verb | Used by | Notes |
|---|---|---|---|
| `/auth/register` | POST | `modules/login/Register` | issue customer JWT like `/auth/login` |
| `/auth/reset` | POST | (reserved) | password reset |
