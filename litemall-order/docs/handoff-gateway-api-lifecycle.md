# Handoff — gateway-api: order lifecycle honesty (packages A–C, 2026-09-05)

Order side SHIPPED on `fix/order` (plan-order-lifecycle-e2e.md). Everything below is
ADDITIVE on existing endpoints, plus one new customer action. Code to THIS.

## 1. Payment (package A) — the SPA copy is now the only thing that is still wrong

The backend now survives every late/asynchronous payment: a PaymentIntent that is
`processing` (SEPA debit) keeps the order alive; a charge that lands after the order was
auto-cancelled is refunded automatically and the customer is emailed. Two SPA facts
remain wrong (`StripeCardForm.tsx`, `PaymentStatus.tsx`):

- `confirmPayment` treats `paymentIntent.status === 'processing'` as "Your payment was
  not completed. Please try another card." For SEPA/bank-debit methods that is FALSE —
  the debit is in flight for days and WILL settle. Show "Your bank is processing this
  payment; we will email you when it is confirmed. Do not pay again." and navigate to
  the order page. Never offer a retry on a `processing` intent — the backend also refuses
  to mint a second intent while one is processing (402 "still being processed by your
  bank").
- Redirect methods (Klarna, iDEAL, Bancontact, 3-DS) return to `return_url: /orders`
  and nothing reconciles. Point `return_url` at `/pay/:orderId/status` and have that page
  poll `GET /srv/order/{id}` until `orderStatus` leaves 101 (or read Stripe's
  `redirect_status` query param for the failed case). The webhook completes the payment
  server-side; the page only has to WAIT for it.
- `POST /srv/order/{id}/actions/pay` after the webhook already settled the same intent
  now answers success (was "invalid state"). A pay on a cancelled order whose intent had
  captured answers a typed failure whose message says the payment "has been refunded
  automatically" — show it verbatim.
- 402 from `POST .../actions/payment-intent` can now carry "has already been paid —
  refresh the page" (paid in another tab / a redirect the SPA never followed): treat as
  success, reload the order.

## 2. Fulfilment visibility (package B) — new customer-facing facts

- `GET /srv/order/{id}/tracking` for a SHIPPED order with no tracking number yet answers
  `{shipped:true, status:"TRACKING_PENDING", carrier, events:[]}` (was `shipped:false,
  NOT_SHIPPED` — the "Shipped" badge next to "Not shipped yet" contradiction). Render
  "Shipped — tracking number on its way".
- `fulfillmentStatus` on `GET /srv/order/{id}` gains two phrases: "Processing — being
  reviewed by our team" (placement parked) and "Could not be fulfilled — our support team
  will contact you" (CJ cancelled; the customer has been emailed).
- New mails the customer receives: `payment-refunded` (stray charge reversed),
  `fulfilment-cancelled` (supplier cancelled, support will contact), `delivered` (order
  closed, return window stated). Their CTAs point at `/order/:id` (guest buyers still hit
  the login wall there — the tokenized guest order view raise from 2026-08-26 stands).

## 3. Closure (package C)

- **New:** `POST /srv/order/{id}/actions/refund/withdraw` (customer, owner-scoped) —
  REFUND_REQUEST (202) → back to PAID or SHIPPED. `handleOption.withdrawRefund` is
  `true` exactly when the order is 202. Typed failure message (not a 500) when the
  request cannot be withdrawn (delivered-order aftersale, or already decided): show it
  verbatim. Fixes the 202 dead end: render a "Withdraw refund request" button wherever
  the order list/detail shows a 202 order, and STOP hiding 202 from the Refunds page
  (`RefundList.tsx` filters on `aftersaleStatus > 0 || handleOption.refund`, which a plain
  refund request satisfies neither of — use `orderStatus === 202 || …`).
- The auto-confirm window is now the admin's `litemall_order_unconfirm` setting (7 days
  in dev) rather than a hidden 15-day code default. Any copy that names the number should
  read it from `/srv/order/{id}` context or say "after the confirmation window".

## 4. Timeline (F-raise, unchanged ask)

`GET /srv/order/{id}/timeline` is still unrendered while `Help.tsx` / `faqData.ts`
promise a status timeline. New change types worth rendering as plain sentences:
`cj_placement` (queued / approved / deferred / placed / requeued), `cj_placement_failed`,
`cj_stall`, `cj_sync`, `payment_reconciled`, `payment_refunded_stray`,
`payment_stray_unrefunded`, `refund_withdrawn`. Operators are now honest (`system` for the
CJ poller's ship, `admin:<id>` for admin actions, `user`).
