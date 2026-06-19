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
| `/srv/order/prepay` (or `/srv/order/{id}/actions/pay`) | POST | `Checkout` payment step, `views/commonViews/cart/Payment` | **pay a placed order** — CARD (Stripe) / WALLET debit. `POST /srv/order/submit` has NO `paymentMethod`; payment is a separate post-submit action the SPA cannot complete until this lands. |
| `/srv/address/list` | GET | `Checkout`, `modules/user/AddressList` | address book — **blocks checkout** (submit needs a saved `addressId`) |
| `/srv/address/detail?id=` | GET | `AddressEdit` | |
| `/srv/address/save` | POST | `Checkout`, `AddressEdit` | create/update — without it a typed address yields no `addressId` |
| `/srv/address/delete` | POST | `AddressList` | |

> **Verified live (2026-06-19) against the running order service — corrections:**
> - The cart REST verbs are mounted under **`/srv/cart/items`** (`/items`, `/items/{cartItemId}`),
>   NOT bare `/srv/cart`. `cartApi.ts` now targets `/srv/cart/items`.
> - `POST /srv/order/submit` exists but takes a **`LitemallPlaceOrderCommand`**
>   `{ cartId, addressId, couponId, userCouponId, message, grouponRulesId, grouponLinkId }`
>   with the buyer bound from the gateway-injected `X-User-Id` header — it does **not** accept
>   inline `items`/`shipping`/`paymentMethod`. `orderSlice.ts` now sends this command shape.
> - End-to-end checkout is therefore blocked on two order/user follow-ups: (1) `/srv/address/*`
>   to produce a saved `addressId`; (2) a post-order payment action for CARD/WALLET.
> - Runtime note: `order-service-app` (:8085) was observed crashing during verification
>   (`NoRouteToHost :8085`); its bring-up/stability is owned by the `order` worktree
>   (shared-DB V20 reinstall + restart per the run-from-MAIN rule).

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

## Owner: auth edge (`/auth/**`, gateway-api `AuthController`)
| Endpoint | Verb | Used by | Notes |
|---|---|---|---|
| `/auth/register` | POST | `modules/login/Register` | issue customer JWT like `/auth/login` |
| `/auth/reset` | POST | (reserved) | password reset |
