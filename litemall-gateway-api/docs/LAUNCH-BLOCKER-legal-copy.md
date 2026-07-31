# ✅ RESOLVED (2026-07-31, Wave 15) — legal copy is live

**Status: closed.** The Wave-7 flag ("copy is a legal deliverable — do not invent
legal text") was discharged in Wave 15: the store owner supplied the seller
identity (name, registered address, jurisdiction) and approved the policy
decisions on the record (30-day return window; EU 14-day withdrawal stated;
Swedish law / Stockholms tingsrätt; ARN + EU ODR referenced), and binding copy
now ships on all four routes:

| Route | Page |
|---|---|
| `/terms` | Terms of Sale — seller identity, ordering/payment/delivery, faults & 3-year complaint right, liability, governing law |
| `/privacy` | Privacy Policy — controller identity, legal bases, processor table (incl. the Wave-15 Meta Pixel), GDPR rights + IMY |
| `/cookies` | Cookie Policy — necessary/analytics/marketing tiers + embedded consent withdrawal |
| `/returns` | Returns & Refunds — 30-day window, statutory 14-day withdrawal, refund tender rules |

Seller identity lives in ONE place: `app/modules/static/seller.ts`. The
`LegalPlaceholder` component is deleted; its absence from the bundle is the
launch-readiness signal the original doc defined. The old 7-day return claim is
gone from all three surfaces (Returns page, FAQ data, PDP badge) — 30 days
everywhere.

**Keep the processor table in sync.** A privacy policy that omits a live
processor is a compliance failure no matter how good the prose is. Any future
wave that adds an integration touching customer data must update `Privacy.tsx`.

Remaining (not launch-blocking, tracked elsewhere):
- Professional legal review of the shipped wording is still recommended — the
  copy is owner-approved but not counsel-reviewed.
- GDPR self-service export/erasure stays on the Wave-7 debt register; the
  policy promises a manual mailbox process with a one-month SLA, which is what
  the code supports today.
