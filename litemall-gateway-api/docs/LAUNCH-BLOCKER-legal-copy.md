# 🔴 LAUNCH BLOCKER — legal copy is outstanding (Wave-7 Task D)

**Status: BLOCKS LAUNCH. Not an engineering task.**

The Wave-7 plan locks the market as **US and EU**, which makes a privacy policy and
cookie consent legal prerequisites rather than polish. Task D says plainly: *"Copy is
a legal deliverable, not an engineering one — if no counsel-reviewed text exists,
FLAG IT; do not invent legal text."* This file is that flag.

## What shipped

The engineering half is **done and verifiable**:

| Route | Page | Footer link that now reaches it |
|---|---|---|
| `/terms` | Terms of Sale | "Conditions of Use" (was `/help`) |
| `/privacy` | Privacy Policy | "Privacy Notice" (was `/help`) |
| `/cookies` | Cookie Policy + consent withdrawal | "Cookie Preferences" (was `/service`) |
| `/returns` | Returns & Refund Policy | "Returns & refunds" (was `/refunds`, the signed-in list) |

All four previously pointed at `/help` or `/service` — four labels, two generic
destinations, no legal content. They are now real, distinct, routed pages.

Each renders `LegalPlaceholder`, a loud "Draft — not legally binding" banner.
**The presence of that component in the bundle means the store is not launch-ready.**
Delete it when the copy lands:

```
litemall-gateway-api/src/main/webapp/app/modules/static/LegalPlaceholder.tsx
```

## What engineering DID write (and stands behind)

Section structure, and **factual descriptions of what the system actually does** —
derived from the code, not drafted:

- **The processor table in `/privacy`** — Stripe (cards + tax), CJ Dropshipping
  (fulfilment: name, full address, contact, items), Matomo (self-hosted analytics,
  consent-gated), Mautic (self-hosted marketing email). The plan requires all four be
  named; they are.
- **Cookie/storage inventory in `/cookies`** — strictly-necessary browser storage
  (auth token, cart, the consent record) vs the optional Matomo analytics cookie.
- **Mechanics** — server-authoritative pricing, card details going straight to
  Stripe, refunds returning to the original tender, dropship items shipping
  separately.

**Keep the processor table in sync.** A privacy policy that omits a live processor is
a compliance failure no matter how good the prose is. Any future wave that adds an
integration touching customer data must update `Privacy.tsx`.

## What COUNSEL must supply

Nothing below was drafted, and none of it should be by engineers.

1. **Terms of Sale** — liability, warranties, governing law, dispute resolution,
   contract formation. Currently `To be supplied by counsel`.
2. **Privacy Policy** — lawful basis per purpose, retention periods, international
   transfer mechanism (**CJ Dropshipping is a non-EU recipient of EU customer
   addresses — this needs an explicit transfer basis**), data-subject rights wording,
   and the controller's legal identity + contact.
3. **Cookie Policy** — the operative wording; the mechanism and the inventory are
   described accurately already.
4. **Returns & Refunds** — see the open question below.

## 🔴 Open questions counsel must settle

1. **The 7-day return window is probably wrong for the EU.** `/returns` says 7 days
   because the Help FAQ has always said 7 days — it is repeated, not invented. EU
   distance selling grants a **14-day** withdrawal right, and this store is launching
   into the EU. Engineering must not pick this number. Whatever counsel decides also
   needs applying to the Help FAQ (`modules/static/Help.tsx`), which is the other
   place it appears.
2. **Controller identity** — no legal entity name, registered address, or contact
   appears anywhere in the app. `/service` currently shows placeholder contact details
   (`+1 (800) 000-0000`, `support@litemall.example`). Both a legal requirement and an
   obviously unfinished customer-facing surface.
3. **Analytics lawful basis** — implemented as opt-in consent (the safer choice), with
   Do-Not-Track as a hard override. Confirm opt-in is the intended basis.
4. **GDPR rights are a manual process.** `/privacy` points data-subject requests at
   customer service, matching the debt register's decision (manual handling behind a
   mailbox + SLA discharges the duty). Counsel should confirm, and the mailbox and SLA
   must actually exist at launch. Do not describe it as self-service.

## How to land the copy

Text-only change; no engineering work should be needed:

1. Replace the prose in `Terms.tsx`, `Privacy.tsx`, `Cookies.tsx`, `Returns.tsx`.
2. Delete `LegalPlaceholder.tsx` and its four imports.
3. Reconcile the return window in `Help.tsx`.
4. Fill in the real contact details in `CustomerService.tsx`.
