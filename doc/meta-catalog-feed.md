# Task: Meta (Facebook/Instagram) product catalogue feed for trovemo.com

> Committed copy of the user-provided task spec (2026-07-29), lightly annotated
> where verified against the codebase — see the Wave-14.1 addendum in CLAUDE.md
> for how this slots into the goods-management worktree.

## Goal
Expose a public, cacheable product feed that Meta Commerce Manager can fetch on a
schedule, so all on-sale products appear in the Trovemo catalogue and become
taggable in Facebook/Instagram posts.

## Deliverable
`GET https://trovemo.com/meta-catalog.csv`  → `text/csv; charset=utf-8`, HTTP 200

Implement in the **goods-management** service (same place the sitemap generator
lives — reuse its product query and slug builder), then expose it at the site
root through **gateway-api**, exactly as `/sitemap.xml` and `/robots.txt` are
routed. Internal path can be `/srv/goods/meta-catalog.csv`.

## Row set
Same selection as the sitemap: all **on-sale** products (`is_on_sale = 1`,
not deleted). ~11,682 rows today. One header row, then one row per product.
No category or homepage rows.

## Columns (exact header, exact order)

```
id,title,description,availability,condition,price,link,image_link,brand,google_product_category,item_group_id,sale_price,inventory
```

| column | source / rule |
|---|---|
| `id` | numeric goods id, e.g. `10000553` — must be stable forever |
| `title` | goods name, tags stripped, max 150 chars, no ALL-CAPS |
| `description` | goods `brief` (or detail) — **strip HTML tags, decode entities, collapse whitespace**, then truncate to 5000 chars on a word boundary. Reuse the same sanitizer written for the PDP head injection (goods 10000001 embeds an `<img>` in `brief` — this is the same bug class). Never empty: fall back to the title if blank. |
| `availability` | `in stock` when inventory > 0, else `out of stock` |
| `condition` | literal `new` |
| `price` | `"<amount> <CURRENCY>"` — amount with 2 decimals, space, ISO code. **Must match the store's real charging currency** (see Open question below — VERIFIED 2026-07-29: checkout charges `LITEMALL_ORDER_STRIPE_CURRENCY`, default `usd`, no prod override set ⇒ `USD` today; read the currency from the same config `GoodsMetaService` uses — one source of truth). |
| `link` | full slugged PDP URL, same builder as the sitemap: `https://trovemo.com/product/10000553-summer-platform-wedge-sandals` |
| `image_link` | absolute public image URL, ≥500×500px, no auth required. Verify it 200s — rows with a broken image are rejected by Meta. (Codebase note: meta endpoint picUrl is RELATIVE `/_cdn` — absolutize with the public base URL, the Wave-13 og:image gotcha.) |
| `brand` | `Trovemo` unless the goods record has a real brand |
| `google_product_category` | optional but improves distribution — map litemall category → Google taxonomy id where a mapping exists, else leave empty |
| `item_group_id` | goods id, for products with size/colour variants |
| `sale_price` | discounted price in the same `"<amount> <CURRENCY>"` format, empty when not discounted. (Codebase note: a live flash-deal swap sets `retail_price` = deal price with `counter_price` = pre-deal anchor ⇒ when counter > retail: price = counter, sale_price = retail; otherwise price = retail, sale_price empty.) |
| `inventory` | integer stock count (summed SKU stock, as the insight layer computes it) |

## CSV correctness (this is where feeds usually fail)
- UTF-8, **no BOM**, `\n` line endings, comma delimiter
- Quote any field containing a comma, quote or newline; escape inner `"` as `""`
- No blank lines, no trailing delimiter
- Strip control characters
- Prices: dot decimal separator, **no thousands separator**, no currency symbol

## Performance / caching
- 11.7k rows must stream, not be assembled in memory as one string — write
  incrementally to the response.
- Generate on a schedule (nightly cron or on-write invalidation) and serve the
  cached artifact; do not run the full query per request. (Codebase note: hook
  where the sitemap regenerates — after the nightly catalog refresh chain.)
- Send `Cache-Control: public, max-age=3600`. Unlike `/srv/goods/meta/<id>`
  this endpoint is safe to cache at Cloudflare — it is one document for the
  whole catalogue, so there is no cross-product leakage risk.
- Allow `facebookexternalhit` and Meta's fetcher through Cloudflare (check Bot
  Fight Mode isn't blocking it) — deploy-time check, main session.

## Acceptance tests
```bash
curl -sI  https://trovemo.com/meta-catalog.csv          # 200, text/csv
curl -s   https://trovemo.com/meta-catalog.csv | head -3
curl -s   https://trovemo.com/meta-catalog.csv | wc -l   # ≈ on-sale count + 1
# every row has 13 fields (naive check breaks on quoted commas — use a CSV-aware check in tests):
curl -s https://trovemo.com/meta-catalog.csv | awk -F',' 'NF!=13 {print NR": "NF}' | head
# no raw HTML leaked into description:
curl -s https://trovemo.com/meta-catalog.csv | grep -c '<'   # expect 0
# spot-check 5 links and 5 image_links return 200
```

## Open question — RESOLVED 2026-07-29
The `/srv/goods/meta/<id>` endpoint returns `currency: "USD"` and that is
correct: Stripe charges `LITEMALL_ORDER_STRIPE_CURRENCY` (order
`application.yml:176`), default `usd`, and no production override exists. The
spec's original "SEK/DKK" note matched nothing in the codebase; if the store
ever switches currency, set the env var and BOTH the meta endpoint and this
feed follow automatically (single config source).

## Not in scope
Meta's Commerce Manager side (creating the catalogue, pointing it at this URL,
setting the daily refresh) — done in the Meta UI, not in code. The
gateway-api edge route `/meta-catalog.csv` → `/srv/goods/meta-catalog.csv` is
done by the MAIN session at merge time (mirror of the /sitemap.xml route).
