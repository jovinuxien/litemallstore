# Autumn Deals — the collection, and how to put it live

**Status:** curated and validated against the LIVE production catalogue
(2026-08-22). Not yet activated — activation is an admin action on
trovemo.com, deliberately not something a worktree does.

This is the content half of Wave 27. The code half (this module) makes the
storefront read whatever season an admin has activated; this document is the
season to activate. Nothing here is hardcoded anywhere in the SPA — change the
page and the storefront changes with it, with no rebuild.

## 1. Why a collection and not `q=autumn`

The strip it replaces ran `GET /srv/search?q=summer&source=cj`. Measured on the
live feed (3,646 on-sale products), the same approach for autumn is not viable:

| Keyword | Products whose title actually contains it |
|---|---|
| `summer` | 18 |
| `autumn` | **5** |
| `fall` | 8 |

Search then relaxes relevance to fill the grid, which is how a diving flashlight
ended up in Summer Deals. Curated by theme, the same catalogue is rich:
~144 Halloween/harvest items, ~96 outdoor lighting, ~31 cosy textiles, ~19
garden tidy-up. Autumn is a merchandising judgement, not a string match.

Worth knowing before anyone plans autumn advertising: **draught-sealing and
weatherproofing — the obvious autumn DIY theme — has 3 products in the entire
store.** That is a sourcing gap, not a curation one.

## 2. The 24 picks

Balanced per the approved composition: cosy textiles, dark-evening lighting,
garden tidy-up, and restrained Halloween. **15 Home & Garden / 9 Hardware** —
both anchor categories. Every id was verified on the live feed as on sale, in
stock, with a real image.

Order matters: `byIds` preserves it exactly, the home rail shows the **first 8**,
and the season's own page shows all 24. The first 8 are deliberately 4 Home &
Garden and 4 Hardware, so the rail reads as the whole store, not one shelf.

| # | Goods id | Product | Price | Category | Why it is in an autumn collection |
|---|---|---|---|---|---|
| 1 | `10034827` | 2-Pack Lighted Fall Garland 16.4ft Total, 40 LED Autumn Ma | €67.10 | Home & Garden | Lighted fall garland, 40 LED maple leaves — the most unmistakably autumn item in the catalogue |
| 2 | `10010060` | Rust Throw Blanket - Soft & Fluffy Fleece, Cute & Aestheti | €58.43 | Home & Garden | Rust fleece throw — the season's colour, and the archetypal cosy-home buy |
| 3 | `10032806` | 2 IN 1 Battery Leaf Blower 18V Leaf Blower Blower Blower W | €46.93 | Hardware | 18V battery leaf blower — the autumn chore, and the only real leaf blower in stock |
| 4 | `10034828` | Softalker Fall Throw Pillow Covers 18x18 Set Of 2 - Rust O | €67.10 | Home & Garden | Rust-orange pumpkin cushion covers — seasonal decor that is not Halloween |
| 5 | `10011986` | 20'' Dusk to Dawn Outdoor Wall Light, Large Black Outdoor  | €148.30 | Hardware | Dusk-to-dawn porch light — evenings draw in; it switches itself on |
| 6 | `10024219` | Outsunny Outdoor Fire Pit, 18 Inch Metal Wood Burning Fire | €169.85 | Hardware | Wood-burning fire pit — the one outdoor-warmth product, and a strong hero |
| 7 | `10034154` | 2-Piece Wrought Iron Candleholder Set, Gray Vintage Table  | €39.50 | Home & Garden | Wrought-iron candleholder set, sold for autumn tables |
| 8 | `10030991` | Telescopic Pruning Saws And Loppers With Long Handles And  | €45.05 | Hardware | Telescopic pruning saw and loppers — autumn cutting back |
| 9 | `10011119` | White Throw Blanket For Couch Birthday Gifts For Women Mom | €81.38 | Home & Garden | Cosy white fleece throw for the sofa |
| 10 | `10010664` | Cross Border Popular Hand Woven Ultra Thick Woolen Blanket | €64.72 | Home & Garden | Hand-woven thick woollen blanket |
| 11 | `10006727` | 2PCS Soft Corduroy Decorative Pillow Covers 18x18 Inch, Bo | €71.43 | Home & Garden | Corduroy cushion covers, boho stripe — texture for the season |
| 12 | `10033324` | Set Of 2 Throw Pillow Covers Vintage Linen Trimmed Cushion | €31.85 | Home & Garden | Vintage linen-trimmed cushion covers, 20" |
| 13 | `10017060` | Halloween Pitch Black Solid Thermal Insulated Grommet Blac | €47.58 | Home & Garden | Thermal insulated blackout curtains — warmth and the darker mornings |
| 14 | `10033182` | Rectangular Doormat 45x75 cm Rubber | €61.05 | Home & Garden | Rubber doormat — muddy-boot season |
| 15 | `10019711` | Outdoor Wall Sconces 2 Pack, Exterior Light Lantern Fixtur | €146.05 | Hardware | Outdoor lantern wall sconces, 2-pack |
| 16 | `10032895` | 2x LED Lamp Solar Light Outdoor Lamp Garden Light Lantern | €71.00 | Hardware | Solar garden lanterns, 2-pack — light with no wiring |
| 17 | `10032878` | wall light with antique beads, 2 x E14 sockets | €74.93 | Hardware | Antique-bead wall light for indoor evenings |
| 18 | `10030984` | 7-Piece Green Garden Tool Set, Garden Tool Set With Wheelb | €58.58 | Hardware | 7-piece garden tool set with wheelbarrow — autumn tidy-up |
| 19 | `10009745` | Mini Walk-in Greenhouse With PVC Cover, 4-Shelf Indoor Out | €105.25 | Hardware | Mini walk-in greenhouse — frost protection as nights cool |
| 20 | `10029359` | Seasonal Decor - 110-Piece Set Of Artificial Fall Flowers: | €14.78 | Home & Garden | 110-piece artificial fall flowers and pampas grass |
| 21 | `10027250` | Vintage Pumpkin Spice Latte Round Sign Retro Autumn Wall D | €21.18 | Home & Garden | Pumpkin-spice retro wall sign — harvest without Halloween |
| 22 | `10034830` | White Cute Ghost Halloween Pillow Covers 18x18 Set Of 2 Sp | €62.78 | Home & Garden | Cute ghost cushion covers — restrained Halloween |
| 23 | `10029417` | Cute Ghost Reading Book Lamp Halloween Gifts Light Up Ghos | €74.25 | Home & Garden | Ghost book lamp — Halloween that still works as a lamp |
| 24 | `10025549` | Black Cat Moon Ground Lamp Black Cat Pumpkin Moon Stake Li | €11.40 | Home & Garden | Black cat and moon stake light — small, cheap, seasonal |

## 3. The page config

The cloned template already carries the rich-text blocks and one `goods-list`
rail. Paste the id list into that rail, in this order:

```json
[10034827, 10010060, 10032806, 10034828, 10011986, 10024219, 10034154, 10030991, 10011119, 10010664, 10006727, 10033324, 10017060, 10033182, 10019711, 10032895, 10032878, 10030984, 10009745, 10029359, 10027250, 10034830, 10029417, 10025549]
```

Component settings: **mode** `byIds`, **limit** `24`, **title** "Autumn picks".

Suggested copy for the opening rich-text block — factual, and it makes no
delivery claim (see `spec-wave26-eu-sourcing.md` for why delivery promises stay
per-SKU):

> **Autumn at Trovemo.** Warmer textures, longer evenings and a garden that
> needs putting to bed. A short, picked list — throws and cushion covers for the
> sofa, lights for the dark half of the day, and the tools for the autumn
> tidy-up.

## 4. Putting it live (admin, no deploy, reversible)

The procedure is `spec-season-collection.md` §4. In short:

1. **Admin → Pages**, filter category `season`, "New from template" on
   *Season spotlight*. Rename the draft to **Autumn Deals** — this name is
   exactly what the header link and the home rail will show.
2. Edit the rich-text block (§3 above).
3. Open the `goods-list` component, switch **mode** from `deals` to `byIds`,
   paste the ids from §3.
4. **Activate.** The strip, the header entry and the "All" drawer entry appear
   together. Deactivating puts them all back to absent — there is no in-between.
5. Optional: **Admin → Social Publishing (Postiz)**, source *DIY page*, pick the
   Autumn page, schedule.

## 5. Two things to check before activating

**⚠ The gateway-api container must carry the Wave-27 build first.** A curated
`byIds` rail resolves through `POST /srv/goods/batch`, which was public for GET
only — verified on production 2026-08-22: `GET /srv/goods/detail` 200,
`POST /srv/goods/batch` **401**. Activate this page on the old container and the
rail renders **empty for every logged-out shopper** while looking perfectly
correct to you, signed in, previewing it. The fix ships in this module's Wave-27
commit. This applies to any curated DIY rail, not just seasons.

**Halloween has a date on it.** Four of the 24 are Halloween pieces, kept to a
minority precisely so the page stays truthful into November. Around 1 November,
swap them or clone the page into a winter collection — one activation, no
deploy. The previous season page stays as a draft and can be reactivated next
year.

## 6. Catalogue hygiene this surfaced (for goods-management)

Not blocking, and not fixed here — these are title-quality problems on otherwise
correct products:

- **68 live products carry a raw supplier prefix in their title**, e.g.
  "Support Pan European：2 IN 1 Battery Leaf Blower 18V…" (pick #3). The fullwidth
  colon suggests it came straight from the CJ feed.
- **2 products are titled "Halloween …" for no reason**, including the thermal
  blackout curtains at pick #13, which are an ordinary year-round product.

Both are visible to customers on the PDP and in the Meta feed.
