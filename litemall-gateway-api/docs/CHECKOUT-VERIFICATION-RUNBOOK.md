# Checkout verification runbook (customer SPA ⇄ order service)

Repeatable pass/fail checks for the customer checkout path. Use this to confirm the two
order-worktree follow-ups have landed and that order placement works end-to-end. Derived from
the live verification on 2026-06-19 (gateway-api Task C).

> **Status (2026-06-27):** both blockers have **landed** in the order service
> (`LitemallAddressController` at `/srv/address/*`; `POST /srv/order/{id}/actions/pay`). The
> gateway-api side is now wired e2e: the `customer-order` route predicate claims `/srv/address/**`,
> and `orderSlice` runs the **two-step place→pay** flow against the `OrderOperationDtoResponse`
> contract (201 created / 422 stock / 200 paid / 402 insufficient balance). Run the checks below
> against a live stack to confirm.

**Run services from the MAIN checkout** (`litemall/`), not a worktree — shared `~/.m2`/single
litemall-db. Worktree refactors only count once merged.

## Prerequisites
- Eureka `:8761`, gateway-api `:8090`, `order-service-app` `:8085`, goods-management, authserver
  all UP and registered. Quick check:
  ```bash
  curl -s -H Accept:application/json localhost:8761/eureka/apps \
   | python3 -c "import sys,json;[print(i['app'],i.get('status')) for a in json.load(sys.stdin)['applications']['application'] for i in [a]]" 2>/dev/null \
   || curl -s localhost:8761/eureka/apps | grep -o '<name>[^<]*' 
  curl -s -m4 -o /dev/null -w 'order health %{http_code}\n' localhost:8085/actuator/health
  ```
- Customer SPA served either by the MAIN dev server (`:9000`) or a worktree dev server
  (`webpack serve --port 9001`), proxying `/srv` + `/auth` → `:8090`.

## A. Endpoint-existence gate (no auth needed — distinguishes 404 from reachable)

Through the gateway, a **404** means the route/handler is missing; **401/200/5xx envelope** means
it is reachable. Run against `:8090`:

```bash
# Already-correct paths (must NOT be 404):
curl -s -o/dev/null -w 'cart/items        %{http_code}\n' localhost:8090/srv/cart/items
curl -s -o/dev/null -w 'order/submit      %{http_code}\n' -X POST -H 'Content-Type: application/json' -d '{"cartId":0,"addressId":1}' localhost:8090/srv/order/submit

# The two follow-ups — PASS when these stop returning 404:
curl -s -o/dev/null -w 'address/list      %{http_code}\n' localhost:8090/srv/address/list
curl -s -o/dev/null -w 'order/{id}/pay    %{http_code}\n' -X POST -H 'Content-Type: application/json' -d '{"paymentMethod":"WALLET"}' localhost:8090/srv/order/1/actions/pay
```

| Probe | Before follow-up | PASS after |
|---|---|---|
| `GET /srv/cart/items` | 200 | 200 |
| `POST /srv/order/submit` | 200 envelope (errno≠0 when anon) | unchanged |
| `GET /srv/address/list` | **404** | **not 404** (401/200) |
| `POST /srv/order/{id}/actions/pay` | **404** | **not 404** (401/200/envelope) |

## B. Authenticated smoke (machine + customer identity)

Order endpoints require identity. The gateway's `IdentityForwardingFilter` injects `X-User-Id`
from a validated customer Bearer; downstream security needs the machine token (client-credentials
from authserver `:8089`). For a controller-only existence check you can hit `:8085` directly with a
machine token; for true identity use a real customer login:

```bash
# customer JWT (seed user) — adjust credentials to your seed data
TOKEN=$(curl -s -X POST localhost:8090/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"<user>","password":"<pass>"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')
curl -s -H "Authorization: Bearer $TOKEN" localhost:8090/srv/address/list      # → the user's address book
```

## C. End-to-end checkout (browser)

Drives the real SPA. Requires: order endpoints from (A) present, a logged-in customer, and at
least one **saved address** (created via `POST /srv/address/save`).

1. Sign in at `/login`; add an item to the cart; go to `/checkout`.
2. Address step shows the saved address (no "address book pending" warning).
3. Payment step → pick WALLET or CARD → **Place order**.
4. PASS: routes to `/order-confirmation/:id`; cart cleared; the order is `PAID`
   (WALLET debited / insufficient-balance surfaces the domain error and creates no paid order).

A headless variant (puppeteer-core + system chrome, capturing console/page errors) lives in the
verification history; re-point it at the served SPA URL and assert zero page errors on
`/`, `/category/:id`, `/checkout`.

## What "done" looks like
- (A) `address/list` and `order/{id}/actions/pay` are no longer 404.
- (B) authenticated `address/list` returns the caller's addresses, scoped by `X-User-Id`.
- (C) a logged-in checkout reaches confirmation with a `PAID` order; a wallet-insufficient attempt
  leaves no paid order.
- Gateway `customer-order` route predicate includes `/srv/address/**` (gateway-api change).

## Specs / cross-refs
- `litemall-order/docs/handoff-gateway-api-srv-address.md` — address-book endpoint spec.
- `litemall-order/docs/handoff-gateway-api-order-payment.md` — payment-action endpoint spec.
- `docs/SRV-FOLLOWUPS.md` — SPA-side record of both gaps.
