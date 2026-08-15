# DE in-band sourcing probe — 2026-08-15 (COMPLETE)

Run: `COUNTRIES=DE LEAVES_PER_L1=99 ONLY_L1="Home, Garden & Furniture|Home Improvement"
MIN_PRICE=11.55 MAX_PRICE=36.95 ./cj-eu-warehouse-probe.sh`

## Run in two halves — the first ran out of CJ points

The morning run covered 24 of 68 leaves before hitting
`Insufficient API points. Used today: 68340  Remaining: 0`. Home Improvement was swept
after the daily reset at 16:00 UTC. **Both anchor L1s are now fully measured, 68 of 68
leaves.**

⚠ The morning aggregate under-reported: rows whose DE cell succeeded but whose
denominator call failed were dropped entirely, hiding four leaves — including Bathroom
Storage (48), Kitchen Storage (38) and Power Tools (17). Never let an ERR in one column
silently discard a row's other columns.

## Totals

| L1 | leaves | in-band | DE | DE% |
|---|---:|---:|---:|---:|
| Home, Garden & Furniture | 24 (partial denominators) | 3,879+ | 315 | ~5.05% |
| Home Improvement | 28 | 6,354 | **423** | **6.66%** |
| **anchor cluster** | **68** | — | **~738** | — |

Home Improvement is the denser half, as Phase 1a predicted (5.18% storewide).
**Hand Tools alone is 277 DE of 632 in-band — 44%, the largest pocket found anywhere.**

### Home Improvement leaves with DE stock

| leaf | DE | in-band |
|---|---:|---:|
| Hand Tools | 277 | 632 |
| Garden Tools | 51 | 861 |
| Tool Sets | 26 | 260 |
| Power Tools | 17 | 303 |
| Wall Lamps | 12 | 617 |
| Kitchen Appliances | 9 | 220 |
| Flashlights & Torches | 7 | 121 |
| Tools Storage | 6 | 86 |
| Downlights | 5 | 49 |
| Home Appliance Parts · String Lights · Home Improvement Materials · Woodworking Machinery · Chandeliers · Welding · Air Conditioning | 1–3 each | — |

18 leaves are configured as sourcing targets (DE ≥ 5), covering ~689 of the ~738 found.
Leaves below 5 are left out: a target costs a paced CJ call every night.

## The band, and why it is in dollars

CJ's `minPrice`/`maxPrice` bound CJ **cost in USD**. Our target is a €25–80 *retail*
band; at margin 2.5 that is €10–32 of cost, and at fx 0.866 that is **$11.55–$36.95**.
Passing the euro figures through would have sourced a quietly different band with no
signal that anything was wrong.

## Result: DE stock is ~17× denser inside the band

| | storewide (Phase 1a) | in-band, Home Garden & Furniture |
|---|---:|---:|
| DE share of CJ supply | 0.30% | **5.05%** (196 of 3,879) |

CJ stocks higher-value goods locally — cheap items are not worth the shelf space — so
filtering to the band concentrates exactly the products we want. The Phase-1a headline
("~3,800 DE SKUs account-wide") therefore *understates* what is reachable for sellable
products.

10 of 24 leaves hold any DE in-band stock, and the distribution is sharply uneven:

| leaf | in-band | DE |
|---|---:|---:|
| Decorative Flowers & Wreaths | 483 | 78 |
| Event & Party Supplies | 587 | 49 |
| Christmas Decoration Supplies | 168 | 25 |
| Curtains | 46 | 15 |
| Fabric | 72 | 13 |
| Apparel Sewing & Fabric | 68 | 8 |
| Cushion Covers | 126 | 5 |
| Comforters · Bedding Sets · Dinnerware | 1,862 | 1 each |

Three leaves hold 152 of the 196. Sourcing targets should name those leaves, not the L1.

## Caveats

- **CJ SUPPLY counts, not our catalogue.** These are products we *could* acquire.
- Counts are per-country queries and must never be summed across countries: a product
  stocked in two countries appears in both.
- Seasonality is visible and worth pricing in — "Christmas Decoration Supplies" and
  "Event & Party Supplies" are two of the top three, in August.

## Next

1. Re-run for `Home Improvement` after the points reset.
2. Then size real `catalog-targets` against the measured leaves (with
   `countryCode: DE`, `minPrice: 11.55`, `maxPrice: 36.95`), rather than the L1.
3. A "ships from Germany" claim is PER-SKU. Nothing here supports a storewide promise.
