# Findings — CJ freight probe (landed-cost plan, step 0) — 2026-11-15

Read-only probe run from the workstation with the prod CJ account, 2026-11-15 22:00–23:57 UTC.
Script: `docker-compose/cj-freight-probe.py`. Sample: 223 on-sale products
(`data-freight-probe-sample-2026-11-15.csv`), stratified by Google category × price band — it
over-represents expensive products, so the shares below are of the SAMPLE, not of the catalogue.
One variant per product (CJ's first), quantity 1, destinations SE, DE, DK, FR.

Data: `data-freight-probe-2026-11-15.csv` (pass 1, origin CN) and
`data-freight-probe-supplier-shipped-2026-11-15.csv` (pass 2, origin = where CJ says the stock is).

## The headline

The plan assumed the problem was "shipping costs more than we charge". The larger problem is that
most sampled products cannot be delivered to our customers at all.

| Of 223 on-sale products | Count | Share |
|---|---:|---:|
| Removed from CJ's shelves (still on sale with us) | 26 | 12% |
| Supplier-shipped, no courier to SE, DE, DK or FR | 112 | 50% |
| Supplier-shipped from Germany, deliverable to Germany only | 17 | 8% |
| Supplier-shipped from Spain, deliverable to DE, DK, FR (not SE) | 2 | 1% |
| Shipped from China, deliverable to all four countries | 66 | 30% |
| … of those, a single-item order to Sweden makes money | 40 | 18% |

Only 66 of 223 can reach Sweden or Denmark. Only 40 do so at a profit when bought alone.

## 1. Supplier-shipped products (131 of 197 with CJ detail)

CJ product type `SUPPLIER_SHIPPED_PRODUCT`: the supplier ships from its own warehouse, and only
on that warehouse's domestic lanes.

- Stock location (CJ `product/stock/queryByVid`): US 71, GB 27, DE 17, US+CA 4, ES 2, none 10.
- `freightCalculate` from China returns an empty list for every one of them, to every destination.
- From the stock country: US, GB and CA stock has no lane to any of our four countries. German
  stock ships to Germany only ("DHL DE to DE", $7.00 or $0.00). Spanish stock ships to DE, DK and
  FR by UPS ($9.92–11.10), not to SE.
- They are concentrated in the expensive bands: 81 of the 131 retail above €50. These are the
  products that ship free today.

What the shop does with them today:

- Checkout: CJ returns no options, so the customer is charged the flat rule and sees no courier.
- Placement (`CjDropshipOrderFacadeImpl`): sends origin `CN` and "CJPacket Ordinary" when no
  option exists. Not tested live, but CJ offers no such lane for these products, so the expected
  result is a refused placement after the customer has paid — a parked order and a refund.
- The `eu_flag` / "in EU stock" badge: 12 sampled products carry it and are supplier-shipped from
  Germany. For a Swedish or Danish customer that badge is wrong — the product cannot be sent to
  them. The checkout's "ships from Germany" origin note has the same problem.

## 2. Removed at CJ (26)

`product/query` answers "Product has been removed from shelves". They are still on sale with us;
9 of them still carry measured EU stock. The nightly sync marks availability, but nothing takes
these off sale.

## 3. China-shipped products (66): what CJ charges vs what we charge

CJ default courier (CJPacket Ordinary when offered, else cheapest), converted at 0.866, against
the flat €6.93 (free from €76.21). 264 priced rows = 66 products × 4 countries.

| Variant weight | Rows | Median CJ shipping € | p90 € | Max € | Above what we charge | Avg shortfall € |
|---|---:|---:|---:|---:|---:|---:|
| ≤ 250 g | 76 | 4.92 | 6.55 | 8.97 | 7% | 0.75 |
| 250 g – 1 kg | 112 | 9.26 | 16.12 | 37.16 | 87% | 5.22 |
| 1 – 3 kg | 40 | 22.00 | 44.38 | 50.24 | 100% | 19.08 |
| 3 – 10 kg | 32 | 64.49 | 124.93 | 136.33 | 100% | 61.76 |
| > 10 kg | 4 | 158.78 | 212.47 | 212.47 | 100% | 169.25 |

| Retail band € | Rows | Median CJ shipping € | Max € | Above what we charge |
|---|---:|---:|---:|---:|
| 5–10 | 84 | 5.87 | 18.03 | 38% |
| 10–15 | 64 | 8.77 | 37.16 | 67% |
| 15–25 | 56 | 13.87 | 50.24 | 84% |
| 25–50 | 12 | 38.32 | 45.81 | 67% |
| 50–76.21 | 28 | 42.00 | 80.43 | 100% |
| 76.21–150 (ships free) | 8 | 70.45 | 136.33 | 100% |
| 150+ (ships free) | 12 | 78.13 | 212.47 | 100% |

- The four destinations cost about the same (median €8.32 SE, €8.84 DE, €9.16 FR, €10.34 DK).
- Weight decides everything. Under 250 g the flat fee covers shipping. Above 1 kg it never does.
- CJ's quote carries no `taxesFee` on any row — the $4.00 "customs duty" of order 11 is not in the
  freight quote. Still a question for CJ support.

Net profit on a single-item order (retail + charged shipping − VAT − CJ cost − CJ shipping −
Stripe 1.5% + €0.25; no duty):

| Destination | Priced products | Lose money | Net under €3 |
|---|---:|---:|---:|
| SE | 66 | 26 | 32 |
| DE | 66 | 25 | 32 |
| DK | 66 | 28 | 39 |
| FR | 66 | 26 | 33 |

By weight (SE): ≤ 250 g 0 of 19 lose; 250 g–1 kg 9 of 28; over 1 kg 17 of 19. Worst cases lose
€65–79 per order (a 15 kg item at €248 with €161 shipping; a 7.3 kg item at €149 that ships free
with €136 shipping).

## Limits of this probe

- Stratified sample: shares are not catalogue shares. A catalogue-wide count needs the product
  type and stock country stored per product (plan step 2).
- First variant only. Three products show CJ cost above our retail; that is the first variant
  being dearer than the one our lowest price comes from, not a pricing bug (not verified per SKU).
- Supplier-shipped lanes were tested to four countries only, cheapest option recorded.
- Placement of a supplier-shipped product was not attempted.

## What this changes in the plan

1. A new step comes first: stop selling what cannot be delivered. Off-sale (reversible) products
   that CJ has removed, and supplier-shipped products with no lane to the customer's country.
   This needs product type and stock country captured at enrichment, and a decision on
   Germany-only stock (off sale, or sold to German addresses only).
2. Step 1 (charge real shipping) is urgent, not cosmetic: 39% of deliverable products lose money
   sold alone, and every one above 1 kg does. The proposed free-shipping cap of €6.93 is needed —
   uncapped free shipping is where the €65–79 losses are.
3. The "in EU stock" badge and filter must not be shown as a delivery promise outside Germany.
