# ADR — CJ Dropshipping create-order ACL in litemall-order

**Status:** Accepted, 2026-06-13. Scope: **ACL + callable contract only**; checkout-routing
deferred (see below).

## Context

goods-management indexes CJ Dropshipping products into the unified search with `cj_<pid>`
ids + `source=cj_dropshipping` (see
`litemall-goods-management/docs/cj-order-placement-handoff.md`). Per user directive, **CJ
order placement is the order service's responsibility**. This ADR records the CJ
create-order ACL built in litemall-order.

## Decision

A self-contained CJ ACL mirroring the existing goods ACL (`LitemallGoodsFacade` +
`GoodsServiceFeignClient` + `FallbackFactory`, commit `942178abb`):

- **Auth (order owns its own):** `CjAuthFeignClient.getAccessToken` →
  `CjTokenService.getValidToken()` (in-memory token + expiry cache, re-auth on near-expiry).
  Email/API key are config-driven (`spring.cjdropship.api.auth.*`); host via
  `spring.cjdropship.api.base-url` — **no secrets/hosts hardcoded in Java**.
- **Create-order:** `CjOrderFeignClient.createOrder(@RequestHeader CJ-Access-Token, request)`
  with a resilience4j circuit-breaker + `CjOrderFeignClientFallbackFactory` and feign timeouts
  (`cj-dropship-order` instance in `config/application.yml`).
- **Facade seam:** `CjDropshipOrderFacade.placeOrder(CjOrderPlacement) → CjOrderResult`.
  Maps a placement (lines `{vid, quantity}` + shipping address) to the CJ request, unwraps
  the response, and throws `LitemallCjOrderException` on any failure (transport, circuit-open,
  or CJ `result=false`). Domain/application code depends only on this interface, never Feign.
- **Callable contract:** `POST /srv/order/cj/orders` (`LitemallCjOrderController`) → facade →
  CJ order id/status; a CJ failure surfaces as HTTP 502 (no half-placed order). The future
  checkout-routing calls the facade directly, not this endpoint.

### Call contract

| direction | shape |
|-----------|-------|
| input | `orderNumber`, shipping (`customerName, phone, countryCode, country, province, city, address, zip`), `lines:[{vid, quantity}]`, `remark` |
| output | `cjOrderId`, `cjOrderNum`, `cjOrderStatus` |

CJ create-order endpoint: `POST {base-url}/shopping/order/createOrder`, header
`CJ-Access-Token`. Note CJ also requires `fromCountryCode` (warehouse source) and may need a
freight/`logisticName` step — exposed on the request DTO; populate at checkout-routing time.

## Verification (live, no real order placed — per decision)

Exercised against the live CJ API on 2026-06-13 (mirrors the Feign clients exactly):

**1. Auth — `getAccessToken`:**
```
POST {base-url}/authentication/getAccessToken  {email, password=<apiKey>}
-> { code:200, result:true, message:"Success",
     data:{ accessToken:<...>, accessTokenExpiryDate:"2026-12-09T22:12:11+08:00" } }
```
Token obtained; `CjTokenService.parseExpiry` parses the offset-datetime expiry.

**2. Create-order — `createOrder` with an INVALID vid (captures validation, NO real order):**
```
POST {base-url}/shopping/order/createOrder   (header CJ-Access-Token: <token>)
body: { orderNumber:"VERIFY-WIRING-0001", shippingCountryCode:"US", shippingCountry:"United States",
        shippingProvince:"California", shippingCity:"Los Angeles", shippingAddress:"123 Test St",
        shippingZip:"90001", shippingCustomerName:"Wiring Test", shippingPhone:"15555550123",
        products:[{ vid:"INVALID_VID_FOR_WIRING_TEST", quantity:1 }] }
-> { code:1600300, result:false, message:"fromCountryCode must not be empty",
     data:null, requestId:"2746af36...", pointsInfo:{ remaining:49850 } }
```
The request authenticated and reached CJ; CJ returned a structured `result:false` validation
error → `CjDropshipOrderFacadeImpl` raises `LitemallCjOrderException("...fromCountryCode must
not be empty")`. **No real order was placed and no funds were spent.** Placing a real order
(funded account + real `vid` + `fromCountryCode`) is left to the order team.

**3. Unit test — `CjDropshipOrderFacadeImplTest`** (mirrors `LitemallGoodsFacadeImplTest`):
asserts the facade maps the placement to the CJ request, unwraps success, and converts both a
CJ `result=false` business error and a transport failure into `LitemallCjOrderException`. It
mocks only the `CjOrderFeignClient` interface and hand-stubs the concrete `CjTokenService`
(subclass override) so it stays clear of the Mockito-2.x/JDK-21 subclass-mock issue. The test
**compiles standalone** against the built classpath (`javac` clean). Running the full module
test suite is currently blocked by **pre-existing** test-infra debt unrelated to this change —
a stray `PostgreSqlTestContainer` (from the initial `26b54fc81` commit) references
`testcontainers-postgresql`, which the order pom does not declare (it declares
`testcontainers-mysql`) — plus the root-pom `mockito-core` 2.28.2 / JDK-21 pin. Both are
flagged as Phase-6 debt (consistent with goods-management); not fixed here.

`mvn -o -pl litemall-order -am compile` clean (JDK 21).

## Deferred follow-up — checkout-routing (NOT built here)

Routing a customer checkout's CJ-sourced lines to this ACL is **deferred** because the
cart/order stack is **Integer-only** for ids:
- `LitemallGoodsId`/`LitemallGoodsProductId` are `Integer` (validated `> 0`);
- `litemall_cart.goods_id` and `litemall_order_goods.goods_id/product_id` are `INTEGER`
  columns in **shared `litemall-db`**;
- the goods Feign ACL takes `Integer`.

A CJ product's string id `cj_<pid>` cannot be carried in a cart/order line today. Wiring it
requires, as a separate effort:
1. a **`litemall-db` migration** adding nullable `cj_pid` (String) + `source` columns to
   `litemall_cart` + `litemall_order_goods`, and the matching aggregate fields;
2. **placement routing**: at `LitemallOrderServiceImpl`, lines with `source=cj_dropshipping`
   go to `CjDropshipOrderFacade.placeOrder(...)` (resolving the CJ `vid`/inventory by `pid`
   from goods-management) instead of the local `LitemallGoodsFacade` stock-reserve;
3. the **gateway-api** customer add-to-cart path so a CJ search hit can enter the cart.

A shared CJ client library (auth/token reused across goods-management + order) is a possible
future refactor; today each module owns its CJ auth.
