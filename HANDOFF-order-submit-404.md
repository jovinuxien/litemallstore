# Handoff — place-order fails from customer SPA (503 then 404)

Reported by the master-checkout session on 2026-06-10. Reproduces from the
customer SPA "Place order" button.

## Browser console
```
POST http://localhost:9000/srv/order/submit 503 (Service Unavailable)
POST http://localhost:9000/srv/order/submit 404 (Not Found)
POST http://localhost:9000/srv/order/submit 404 (Not Found)
```
`:9000` is the customer SPA webpack dev-server, which proxies `/srv/**` →
gateway-api `:8090`, which routes `/srv/order/**` → `lb://order-service-app`.

## Diagnosis — two distinct faults

### A. 503 (transient / runtime)
A 503 from the `lb://order-service-app` route means Spring Cloud Gateway's
load balancer had **no healthy instance** registered in Eureka at that moment —
order service was down, still booting, or not yet registered. The service name
is correct (`spring.application.name: ORDER-SERVICE-APP`). Likely just "service
not up yet"; confirm it appears in Eureka (`http://localhost:8761`). Not a code
bug on its own — but worth noting in the order README/troubleshooting.

### B. 404 (the real blocker — path mismatch)
The SPA POSTs to `/srv/order/submit` (`gateway-api`
`src/main/webapp/app/shared/reducers/orderSlice.ts:58`:
`baseAxios.post(`${BASE_URL_CONTEXT}/order/submit`, payload)`).

But on this branch `LitemallOrderRestController` (`@RequestMapping("/srv/order")`)
exposes order creation at a **bare** `@PostMapping` → `POST /srv/order`, with no
`/submit` segment. So `/srv/order/submit` matches no handler → 404 even when the
service is healthy.

`interfaces/rest/LitemallOrderRestController.java:38-43`:
```java
@PostMapping                                  // -> POST /srv/order  (NOT /submit)
public ResponseEntity<OrderOperationDtoResponse> createOrder(
        @RequestBody LitemallPlaceOrderCommand command) {
    LitemallOrderOperationResult result = orderOrchestrationService.createOrder(command);
    return buildResponse(result);
}
```

## Decision needed (your call, plan first per the locked rule)
The order worktree owns the backend contract; the SPA path lives in the
`gateway-api` worktree (out of scope here). Two options:

1. **Expose `/submit` on the controller** (`@PostMapping("/submit")`) to match the
   SPA's existing call. Lowest-friction, no cross-worktree change. Recommended.
2. Keep `POST /srv/order` as canonical and ask the `gateway-api` worktree to
   change `orderSlice.ts` to `POST /srv/order`. Requires coordinating that
   worktree.

Also worth checking while here:
- `createOrder` takes `@RequestBody LitemallPlaceOrderCommand` only — no
  `X-User-Id` header binding like `cancelOrder`/`list` do. Confirm the customer
  identity reaches the command (gateway-api's `IdentityForwardingFilter` adds
  `X-User-*`); a place-order with no user will likely fail downstream.
- This maps cleanly onto the existing order task: "DDD order endpoints respond
  cleanly behind the gateway" + `handleOrderCreation` wiring.

## Suggested acceptance
- From the customer SPA, "Place order" returns 2xx with an order id (no 503/404).
- `POST /srv/order/submit` (or the agreed canonical path) reaches
  `orderOrchestrationService` and emits `LitemallOrderCreatedEvent`.

## Resolution (fix/order)
- **404 — fixed (option 1):** `createOrder` is now `@PostMapping("/submit")` →
  `POST /srv/order/submit`, matching the SPA. No `gateway-api` change needed.
- **Identity — fixed:** `createOrder` now binds `@RequestHeader("X-User-Id")` (as
  `list`/`cancel` do) and rebuilds the command with the header userId as
  authoritative, ignoring any body-supplied `userId`. This closes both the
  null-userId failure (`placeOrder` requires a userId) and the IDOR vector
  (placing an order on behalf of another user). No `UserContext` fallback needed.
- **503 — runtime only, no code change:** `lb://order-service-app` had no healthy
  instance in Eureka at that moment (service down / still booting / not yet
  registered). Confirm `ORDER-SERVICE-APP` appears at `http://localhost:8761`
  before retrying; this is a deploy/ordering concern, not a code bug.
