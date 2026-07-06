# Phase 6 — Customer SPA (gateway-api) integration runbook

Branch `fix/gateway-api`. Scope: `litemall-gateway-api/` only (customer gateway +
customer SPA). This documents what the Phase-6 integration wired up, how to build
and verify it, and the cross-module follow-ups it depends on.

## What changed (Tasks A/B/C + the foundation they sit on)

Phase 5 migrated the customer modules/views into `src/main/webapp/app/` but never
brought over the trees they import or wired them into the app, so the SPA could
not build or run. Phase 6 made the **home → product(+facets) → cart → checkout**
path real:

**Foundation (Phase 0)**
- `npm install` at the workspace root (deps hoisted; `@litemall/shared` symlinked).
- Added scss/css/asset loaders + `onlyCompileBundledFiles` to both webpack configs
  (`webpack/webpack.config.js`, `webpack.config.prod.js`); added `app/declarations.d.ts`
  for style/asset imports. `onlyCompileBundledFiles: true` means **only the routed
  import graph is type-checked** — peripheral views not yet migrated (blog, account,
  support, wishlist…) stay out of the build until a later phase routes them.
- Declared the runtime deps (`react-bootstrap`, `bootstrap`, `bootstrap-icons`) and
  loaders in the customer `package.json`; added a `build` script.
- Registered all customer slices in `app/config/store.ts`
  (`customerAuth, home, category, product, productDetail, search, cart, order`).
- Wired real routes in `app/App.tsx` behind `app/Layout.tsx` (nav shell) with
  `app/shared/auth/CustomerProtectedRoute.tsx` gating checkout/orders; added a lean
  `app/modules/login/CustomerLogin.tsx` (customer `/auth/login`).
- Imported bootstrap + bootstrap-icons CSS once in `app/index.tsx`.

**Task A — home bug-free** (`app/modules/home/Home.tsx`)
- Self-contained connected component; guards every list before `.slice`/`.map`,
  stable `id` keys, a `priceNum()` helper that reads `LitemallMoney {amount}` or a
  plain number. Removed dead code (unused svg/icon imports, unrendered lazy cards,
  the `components` memo, `data.iconProducts`). Deleted `Home1.tsx`.

**Task B — faceted product page** (`app/modules/product/List.tsx`,
`app/modules/product/searchSlice.ts`, `app/shared/model/search/search.models.ts`)
- Home tiles route to `/category/:id` / `/products?category=` / `/search?q=`.
- Left sidebar: category facet (single-select), brand facet (multi-select), price
  range, active-filter chips, clear-all, paging — all driven by `GET /srv/search`.
  No SQL fallback on the faceted path.

**Task C — checkout** (`app/views/commonViews/cart/Checkout.tsx`,
`app/shared/reducers/cartSlice.ts`, `app/shared/reducers/orderSlice.ts`,
`app/views/commonViews/cart/OrderConfirmation.tsx`)
- Multi-step review → shipping → payment-method (card / **wallet**) → place order →
  confirmation. Empty-cart guard, loading/error states. Fixed the `kommune` handler
  (it used to overwrite `region`) and the stub `handleSubmit`.
- `cartSlice` is the single source of truth for submitted line items and was ported
  onto the **customer Bearer scheme** (`baseAxios` + `customerToken`), dropping the
  legacy `X-Litemall-Token` / `sessionStorage 'token'` scheme.

## Build / verify (the hard gate)

```bash
# from the workspace root (…/litemall-wt/gateway-api)
npm install                                   # one-time; deps hoist, never commit them
cd litemall-gateway-api/src/main/webapp
npm run build                                 # webpack prod → target/classes/static  (MUST be clean)
npm run webapp:dev                            # dev server on :9000, proxies /srv,/auth,/wx → :8090
```

`npm run build` compiles clean (bundle-size warnings only). `node_modules/` and
`package-lock.json` are gitignored — do not commit them.

## Manual end-to-end walkthrough (run once the dependent backends are up)

Live data needs `goods-management` (catalog + OCS `/srv/search`) and `order`
(`/srv/cart`, `/srv/order`) running behind the gateway on `lb://` (8090), plus a
customer able to sign in at `/auth/login`. Then, with `npm run webapp:dev`:

1. **Home** (`/`): hero banner, hot-and-new, categories, featured banners, and deals
   populate from live data; no console null/`.slice`/duplicate-key errors.
2. **Facets**: click a category/deal tile → product page; the sidebar shows
   category/brand/price facets from `/srv/search`; selecting facets filters the list,
   chips appear, paging works, "Clear all" resets.
3. **Detail**: click a product → lean detail (gallery, price, add-to-cart).
4. **Checkout**: add to cart → `/cart` → checkout (redirects to `/login` if anonymous)
   → shipping → payment method (card or wallet) → place order → confirmation. A
   stock/payment/balance failure shows the domain error and leaves no half-placed
   order (cart untouched).

## Cross-module follow-ups (NOT done in this worktree)

- **goods-management** — `SearchService` must surface the OCS **facet buckets** in
  the `GET /srv/search` response `data` (category + brand buckets, overall price
  range). It currently drops them. The sidebar codes against the agreed shape in
  `app/shared/model/search/search.models.ts` and tolerates a partial payload until
  this lands.
- **order** — confirm/implement the customer **cart + order REST contract** consumed
  here:
  - `GET /srv/cart/index`, `POST /srv/cart/add`, `PUT /srv/cart/update`
  - `POST /srv/order/submit` with body
    `{ items:[{goodsId,productId,number,price}], shipping:{…}, paymentMethod:'CARD'|'WALLET' }`
    → `{ errno, errmsg, data:{ orderId, orderSn, actualPrice, paymentMethod } }`;
    validates price/stock, reserves stock, debits the wallet for `WALLET`, and
    returns a non-zero `errno` (creating no order) on stock/payment/balance failure.
  - `GET /srv/order/list` for the order-history view (the `/orders` route is a
    placeholder pending this).

## Scoped-out (deliberately not in this worktree)

- Peripheral half-migrated views (blog, account/*, wishlist, support, payment, the
  `productDetailComponent/*` tree, `StarZone`, `relatedSlice`) remain unmigrated and
  unrouted; `onlyCompileBundledFiles` keeps them out of the build. Migrating/routing
  them is a follow-up.
- Real Stripe SDK wiring (card capture) is left as a stub on the payment step; the
  method selection + wallet path are implemented.
