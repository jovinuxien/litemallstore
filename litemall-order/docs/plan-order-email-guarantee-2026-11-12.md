# Plan — order email guarantee + CJ fallback address + IOSS records (2026-11-12)

Status: **WRITTEN, NOT APPROVED, NO CODE YET.** The user asked for this plan on 2026-11-12
and asked for it to be saved for the next session. Nothing below has been built.

## Why this plan exists

Order 11 (sn 20260802682312, €8.90, Sweden) was finally placed at CJ on 2026-11-10 as
`SD2611101824320648300` after `CJ_IOSS_TYPE=3` was set in prod. The user then asked how it
"slipped through without an email". It did not: a read-only prod check showed order 11
belongs to user 8 (`guest-b90fda3a8078`, claimed, `is_guest` 0) and that account HAS an
email. The investigation still found real gaps in the code, listed here.

## What is wrong

1. **The server accepts an order from an account with no email.** Only the browser form
   asks for one. The order service never checks `litemall_user.email` at submit or pay.
2. **The checkout places the order even when saving the email fails.**
   `Checkout.tsx` ~:821-838 posts `authApi.profile({email})` best-effort; a refusal sets an
   inline notice and the order is still placed. The client check is `/^\S+@\S+\.\S+$/`
   while the server's `AccountService.normalizeEmail` is stricter, so an address can pass
   the form and be refused by the server.
3. **A buyer with no email is skipped silently.** `CustomerMailEnqueueListener` (:205-207)
   writes no outbox row and no log line. `CjFulfillmentService.customerEmailOf` (:276-283)
   swallows exceptions and returns null.
4. **The CJ fallback address in production is the placeholder `orders@litemall.dev`.**
   `application.yml:106` reads `${CJ_CUSTOMER_EMAIL_FALLBACK:orders@litemall.dev}` and
   `docker-compose.prod.yml` has no passthrough for it, so setting it in `.env.prod` alone
   would do nothing. Used at `CjDropshipOrderFacadeImpl.java:311-312`.
5. **The notes say omitting `iossType` inherits the CJ dashboard setting.** Order 11 proved
   that false: CJ refused with "7001: Please enter a IOSS number" until an explicit
   `iossType=3` (CJ's IOSS) was sent. Still wrong in: `application.yml:94-103` comment,
   `litemall-order/docs/adr-cj-lifecycle-parity.md:38-47`, the CLAUDE.md `order` block, and
   the memory notes of 2026-11-09 / 2026-11-10.

## The changes

**Step 1 — checkout (gateway-api, SPA only)**
- If the email save is refused or fails, stop before placing the order, reopen the delivery
  step and show the server's reason.
- Render the order service's new refusal (step 2) verbatim, in en/sv/da.

**Step 2 — order service**
- At submit, refuse with a typed 422 ("add an email address to place this order") when the
  account's email is empty.
- Kill switch, default on, with an explicit yml placeholder AND a prod compose passthrough,
  so it can be turned off by a container recreate alone.
- WARN log when a customer mail is skipped for a missing email, and when the CJ fallback
  address is used.

**Step 3 — configuration**
- Add the `CJ_CUSTOMER_EMAIL_FALLBACK` passthrough to `docker-compose.prod.yml` and set it
  to `support@trovemo.com` in `.env.prod` (recreate order only).

**Step 4 — records**
- Correct the `iossType` statements in the yml comment, the ADR, CLAUDE.md and memory:
  "explicit 3 is required; omitting the field does NOT inherit the dashboard setting".

## Order of shipping

Checkout first, then the order service. In the other order, a customer on the old page
whose email save failed would meet the new refusal with no way to fix it from the form.

Both jars are built on the workstation (never on the VPS). The user runs the ship scripts
via `!` — prod ssh/scp are refused in-session. No migration; no other container touched.

## Proof

- Module tests for the refusal, the kill switch and the two WARN lines, with real
  "Tests run:" counts (order baseline 415 / 0).
- Headless run of the built checkout: a failed email save blocks the order; a valid one
  goes through. jest + tsc + `i18n:check` green (gateway-api baseline 68 suites / 516).
- On dev, an email-less account gets the 422 and an account with an email places normally.

## The cost

A customer whose email the server rejects must correct it before paying. Today they would
pay and never receive a mail. The kill switch reverses the server half without a rebuild.

## Not in this plan (each waits on something outside the code)

- **Declared amount / VAT base.** CJ charged IOSS VAT $0.26 (base ≈ $1.04) on order 11,
  not on the intended 10.28 USD `shopAmount`. Needs from the user:
  `docker logs litemall-prod-order-1 2>&1 | grep -E "declared shopAmount|20260802682312" | tail -20`
  and CJ support's answer on which field and currency they read. Knob if needed:
  `LITEMALL_ORDER_CJ_DECLARED_AMOUNT_CURRENCY=store`.
- **Landed-cost pricing.** CJ charged $9.60 for order 11 (product $0.72, shipping $4.61,
  customs duty $4.00, IOSS VAT $0.26, handling $0.01) against €8.90 ≈ $10.28. Pricing uses
  product cost only (flat freight €6.93, floor €5). Needs CJ's answer on the $4.00 duty;
  short term the price floor can be raised by env; a real fix is a goods-management plan.
- **Address quality.** Order 11 had city "Skåne" for postcode 21536 (Malmö) and a Danish
  +45 phone on a Swedish address. Worth its own look at the checkout form.
- **Order 11's confirmation mail** was never sent (paid before prod mail worked).

## Open questions for the user

- Was the guest checkout for order 11 done with `support@trovemo.com`? If yes, the shipped
  mail goes to that inbox when CJ marks the order shipped.
- Approve all four steps, or drop some?
