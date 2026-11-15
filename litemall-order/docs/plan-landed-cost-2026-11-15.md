# Plan — landed cost: stop selling orders that lose money (2026-11-15)

Status: **WRITTEN, NOT APPROVED, NO CODE YET.** The user asked for this plan on 2026-11-15
after order 11 cost more at CJ than the customer paid.

## What the numbers say

### Order 11 is not today's problem

Order 11 (2 Aug) cost $9.60 at CJ — product $0.72, shipping $4.61, "customs duty" $4.00,
IOSS VAT $0.26, handling $0.01 — against 8.90 charged. It was sold under the OLD pricing:
retail = cost × 1.25, no price floor. That product is off sale today: at the current anchor
margin (× 2.5) it would retail at about €1.56, under the €5.00 floor.

### Today's catalogue, modelled

Live feed, 2026-11-15: 21,964 products, median €34.80. 17.0% sit at €5–10, 30.9% at or above
the free-shipping line.

Model for a single-item order: margin 2.5, flat shipping €6.93 (free from €76.21), Stripe
1.5% + €0.25, VAT 25% (SE/DK) on the goods value plus CJ's 3% fee on the VAT, fx 0.866.
Net profit in EUR:

| Retail | Ship $4.61, no duty | Ship $4.61 + $4 duty | Ship $8, no duty | Ship $8 + $4 duty |
|---:|---:|---:|---:|---:|
| 5.00 | 4.22 | 0.76 | 1.29 | −2.18 |
| 10.00 | 5.86 | 2.39 | 2.92 | −0.54 |
| 15.00 | 7.50 | 4.03 | 4.56 | 1.10 |
| 25.00 | 10.77 | 7.31 | 7.84 | 4.37 |
| 50.00 | 18.96 | 15.49 | 16.02 | 12.56 |
| 76.00 | 27.47 | 24.01 | 24.54 | 21.07 |
| 80.00 (ships free) | 21.96 | 18.49 | 19.02 | 15.56 |

With the one shipping price we have ever seen ($4.61) and no duty, no product on sale loses
money sold alone. If shipping is $8 and the $4.00 duty is real and per order, everything
under about €11.65 loses — 4,545 products, 20.7% of the catalogue.

### Where the real exposure is

1. **We never compare CJ's shipping price with what we charge.** The checkout already
   fetches CJ's real price for the basket (`CjFreightQuoteService.options`, cached 15 min)
   and then charges the flat €6.93 regardless (`CheckoutSummaryService.resolveFreight`,
   `FreightCalculationService` short-circuits CJ carts to the flat rule). The anchor is
   Home, Garden & Furniture plus Home Improvement — tools and furniture, where one parcel
   can cost several times €6.93. The model above has a single data point for a very small
   item.
2. **Free shipping from €76.21 is unconditional.** 30.9% of products ship free whatever CJ
   charges. A bulky €80 item has about €22 of room before VAT-inclusive profit is gone.
3. **The $4.00 "customs duty" is unexplained.** The EU charges no customs duty under €150.
   If CJ adds it to every EU order it costs more than the flat shipping fee recovers on
   small orders. Waiting on CJ support.
4. **VAT takes a fifth of the retail price.** Prices are VAT-inclusive and CJ bills the VAT
   to us. On order 11 CJ used a base of about $1.04, not the declared amount; once CJ reads
   the declared amount correctly, VAT per order rises to 16–20% of retail. That is correct
   and the model already assumes it, but nothing in the pricing rule accounts for it.
5. **Nothing stores or shows landed cost.** `productWeight` / `packingWeight` are parsed
   from CJ and thrown away (kept only as a display attribute). Insight shows margin on
   product cost alone.

## The plan

### Step 0 — measure before changing anything (no deploy)

- **Freight probe.** A read-only script, run from the workstation with the prod CJ
  credentials (the `cj-eu-warehouse-probe.sh` pattern): about 200 on-sale products sampled
  across price bands and both anchor categories, CJ `freightCalculate` to SE, DE, DK, FR,
  recording the default option's price and the product weight. One call per second, about
  15 minutes. Output: a CSV plus a table of "CJ shipping vs what we charge" by price band
  and weight. This is the gate: it tells us whether steps 1 and 2 are urgent or cosmetic.
- **Two questions to CJ support** (user-side): what the $4.00 customs duty on order 11 is
  and whether it applies to every EU order; which field and currency CJ reads for the
  declared amount.

### Step 1 — charge what shipping costs (order service + storefront copy)

At checkout, for CJ baskets: charged shipping = the larger of the flat rule and CJ's real
price for the default courier. Free shipping from €76.21 covers real shipping up to a cap
(proposed: the flat fee, €6.93); above the cap the customer pays the difference. Courier
upgrades keep working as now (delta on top).

- One implementation, used by both preview and submit, in the existing
  `CjFreightQuoteService` seam. No migration.
- If CJ's price is unavailable (outage, no lane), fall back to today's flat rule — never an
  error, and logged.
- Kill switch with yml placeholder and prod compose passthrough; off = today's behaviour.
- Storefront: the checkout already renders the server's freight figure. The delivery page
  and FAQ say "flat €6.93, free from €76.21" — they change to "from €6.93; larger or
  heavier items show their shipping cost at checkout". en/sv/da.

### Step 2 — landed cost in the catalogue (goods-management)

- Capture `packingWeight` (and the probe's shipping estimate where known) on the CJ
  snapshot at enrichment — rides the existing rotation, no new CJ call loops. Needs one
  migration (next free version; check `flyway_schema_history` first).
- Insight: add estimated shipping, VAT and net contribution per product beside the
  existing margin, null when not yet known (never 0).
- A landed-cost guard at promote, mirroring the price floor: a product whose estimated net
  contribution is under a configured minimum goes OFF SALE with a reason (reversible),
  default off. With step 1 live this guard matters mostly for free-shipping items.

### Step 3 — only if step 0 says so

- Raise the price floor by env (`LITEMALL_GOODS_PRICE_FLOOR`, recreate only) if the duty is
  real and per order: €12 would remove the loss-making band in the worst case above.
- A minimum basket value at checkout, if CJ confirms a fixed per-order charge.

## Order of work

Step 0 first; it needs no deploy and decides the rest. Step 1 next: it closes the largest
unknown with the data the checkout already has, in one service. Step 2 after, because it
needs a migration and a few enrichment cycles to fill.

## Proof

- Step 0: the CSV and the table, committed under `litemall-order/docs/`.
- Step 1: module tests with real "Tests run:" counts (order baseline 425 / 0) for the
  larger-of rule, the free-shipping cap, the fallback and the kill switch; preview and
  submit proven to charge the same figure; headless checkout run against a stub in en/sv/da.
  Dev has no CJ credentials, so the first non-flat figure is seen on prod after deploy.
- Step 2: module tests, a dry-run count of what the guard would take off sale at several
  thresholds before it is ever switched on.

## The cost

- Step 1 ends "flat shipping on everything". Heavy items will show a higher shipping price
  at checkout, which will lose some sales that were losing money anyway. Free shipping
  becomes "free up to €6.93 of shipping cost".
- Step 2 will take some products off sale once the guard is switched on.

## Decisions for the user

1. Run the freight probe (step 0) first, or go straight to step 1?
2. Step 1 rule: larger-of flat and real, as proposed — or pass CJ's real price through
   exactly (cheaper than €6.93 for small items, which lowers revenue on small orders)?
3. Free shipping: cap at €6.93 of real cost as proposed, a higher cap, or drop free
   shipping for CJ baskets?
4. Target minimum net profit per order for the step 2 guard (the table above shows what
   each price point yields).
