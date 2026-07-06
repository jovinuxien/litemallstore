# CLAUDE.md — litemall Per-Worktree Tasks

## Per-worktree tasks

> Active fix branches launched via `open-fix-worktrees.sh`. Each Claude session
> opens in `../litemall-wt/<short>` on branch `fix/<short>` and is told to read
> the matching `### Worktree: <short>` block below as its sole task. Edit the
> Task / Acceptance lines to redirect a worktree.
>
> **Wave 2 (2026-07-06) — upstream feature-parity gaps.** The previous
> assignments (OCS pipeline, wallet absorption, checkout gaps, edge gaps) are
> all merged to master; their specs live in git history. This wave closes the
> highest-value gaps vs upstream `linlinjava/litemall` and `crmeb_java`:
> coupons, group-buy (groupon), aftersale/RMA, and the collect/footprint/
> feedback/comment-post engagement verticals.
>
> **Dependency order:** `promotion` and `goods-management` are independent —
> start both first. `order` consumes promotion's coupon handoff spec. The two
> gateways wire SPAs last (or in parallel against committed handoff specs,
> same `docs/handoff-*.md` pattern used in Wave 1).

### Worktree: `promotion`
- **Branch:** `fix/promotion`
- **Path:** `../litemall-wt/promotion`
- **Scope:** `litemall-promotion-service/` only. Read-only mapping checks against `litemall-db` are fine. Contract changes other services need are committed as handoff specs under `litemall-promotion-service/docs/` — do NOT edit `litemall-order/` or the gateways here.
- **Context — the coupon/group-buy code already EXISTS on this branch; the task is to revive it against today's master and prove it live.** `fix/promotion` (tip `83877077b`) carries Phases 1–3: coupon + combination (group-buy) verticals with customer AND admin controllers (`LitemallCouponController`, `LitemallCombinationController`, `interfaces/rest/admin/*`), campaign vertical + RFM/segmentation targeting, and Matomo/Nutch/Mautic ACLs (disabled by default). But the branch is ~178 commits behind master, and master's promotion-service meanwhile gained the seckill + bargain verticals in `LitemallPromotionRestController`. Known landmines:
  - **Flyway renumbering.** The branch's V17/V18 migrations collide with master's shared-dev-DB history (master is at V28+; V17 was already fought over once — see the V17 branch-collision incident). Check `flyway_schema_history` FIRST and renumber to the next free version. Never `flyway repair`.
  - **Boot bean-conflict.** A promotion-service startup bean conflict was recorded 2026-07-05 (gateway-admin follow-up). The service must actually start before anything else matters.
- **Task:**
  1. **Merge master into `fix/promotion`** and reconcile: seckill/bargain (from master) and coupon/combination/campaign (from this branch) coexist in one service that compiles and boots. Renumber migrations; fix the bean conflict.
  2. **Coupon vertical live:** claimable-coupon list, my coupons, claim (领券), exchange-by-code, and a "usable for this checkout" query (amount/goods-scope aware) on the customer surface; create/CRUD + issue-records + direct-grant on the admin surface. Identity from gateway-injected `X-User-Id` (no caller-supplied userId — same IDOR rule as order).
  3. **Combination (group-buy) vertical live:** rule browse (active group-buys + discount + required headcount), start a group / join an open group, my groups, expiry sweep; admin rule CRUD + activity monitoring.
  4. **Emit handoff specs** under `docs/`: (a) the coupon validate/redeem/release contract `litemall-order` calls at submit (include exactly-once redemption semantics and failure-release), (b) the groupon-priced-submit contract, (c) route + endpoint lists for `gateway-api` and `gateway-admin` (note: the customer SPA stub currently calls `/srv/coupon/mylist` — the spec decides whether the gateway maps that path or the SPA re-points to `/srv/promotion/...`).
- **Acceptance:**
  - `mvn -q -o -pl litemall-promotion-service -am compile` clean; service boots on its port with no bean conflict; Flyway applies cleanly on the shared dev DB (no checksum halt, no version collision).
  - Live: claim a coupon → it appears in my-coupons → the usable-at-checkout query returns it for a qualifying cart total and excludes it for a non-qualifying one; exchange-by-code works; admin create/issue-records round-trip.
  - Live: start a group on a group-buy rule, join it from a second user, state transitions recorded; admin monitoring lists the activity.
  - Seckill and bargain still work exactly as on master (regression check).
  - The three handoff specs are committed under `litemall-promotion-service/docs/`.

### Worktree: `goods-management`
- **Branch:** `fix/goods-management`
- **Path:** `../litemall-wt/goods-management`
- **Scope:** `litemall-goods-management/` only. Read-only checks against `litemall-db` fine; gateway/SPA wiring is the `gateway-api`/`gateway-admin` worktrees' job — verify here with curl.
- **Context:** previous assignment (OCS pipeline §18/§19) shipped and merged. New task: the engagement verticals the customer SPA already stubs. The SPA pages exist and call `/srv/collect/list`, `/srv/footprint/list`, `/srv/feedback/submit` behind an `isMissingEndpoint` guard — the backend endpoints simply don't exist. The legacy tables (`litemall_collect`, `litemall_footprint`, `litemall_feedback`, `litemall_comment`) and litemall-db domain types already exist — reuse them, no new migrations expected.
- **Task:**
  1. **Collect/favorites** — `/srv/collect`: list (paged), add-or-toggle (goods, optionally topics like upstream), delete. 
  2. **Footprint** — `/srv/footprint`: list (paged, newest first), record (POSTed by the SPA on product-detail view; dedupe same goods/day like upstream), delete.
  3. **Feedback** — `/srv/feedback`: submit (type + content + optional images).
  4. **Comment write path** — `POST /srv/comment/post` (goods review: star + content + optional images). Decide and document the purchase check: v1 may trust the authenticated user with a `hasPurchased` flag left false, or verify via an order facade — write the decision down; don't silently skip it.
  5. **Admin surface** — `/srv/private/admin/{collect,footprint,feedback}/list` mirroring the existing admin list endpoints (user/address pattern), so gateway-admin can page them.
  - All identity from the gateway-injected `X-User-Id` header — no caller-supplied `userId` params anywhere (the cart-IDOR rule).
- **Acceptance:**
  - `mvn -q -o -pl litemall-goods-management -am compile` clean.
  - With the service up: add→list→delete round-trips for collect and footprint; feedback submit persists and shows in the admin list; `POST /srv/comment/post` creates a review that `GET /srv/comment/list` then returns for that goods.
  - Every endpoint scopes to the header user; requests for another user's data return only the caller's rows.
  - The exact request/response shapes match what the SPA stubs already send (check the slices under `litemall-gateway-api/src/main/webapp/app/` read-only), so the gateway-api worktree only has to remove the guards — record any unavoidable shape difference in a short handoff note under `docs/`.

### Worktree: `order`
- **Branch:** `fix/order`
- **Path:** `../litemall-wt/order`
- **Scope:** `litemall-order/` only. Consumes the promotion coupon handoff spec; if it isn't committed yet, agree the contract with the `promotion` worktree first and build behind an ACL facade so the seam is stable.
- **Task A — coupon application at checkout.** Order's domain already carries coupon/groupon validation value-objects but nothing reaches them. Mirror the `LitemallGoodsFacade` pattern: a `LitemallPromotionFacade` ACL (Feign underneath, timeouts + circuit-breaker like the goods client) that, on `POST /srv/order/submit` with a `couponId` in the body: validates the coupon (ownership, validity window, min-spend, goods scope), applies the discount to the order total, and marks it redeemed exactly-once in the same transaction as order creation — released/rolled back if placement fails. Invalid/expired/not-owned coupon → 422, no order created, coupon untouched. Promotion-service down → placement proceeds without the discount ONLY if no couponId was sent; with a couponId, fail cleanly (never silently drop a discount the customer selected).
- **Task B — aftersale/RMA vertical for local orders.** Today order has the refund *action* and the CJ dispute vertical, but no aftersale workflow like upstream (`WxAftersaleController`/`AdminAftersaleController`). Mirror the CJ dispute vertical's DDD shape: customer `POST /srv/order/{orderId}/aftersale` (type: refund-only / return-and-refund; reason + optional images), list/detail/cancel; admin `/srv/private/admin/aftersale` list + approve/reject, approval flowing into the existing tender-parity refund path (wallet refund = min(actual, ledger debit), CARD = PSP seam). Status transitions persisted through the existing single-transition-source state machine + timeline. New migration numbered after checking `flyway_schema_history` (V28 was the last known — verify).
- **Acceptance:**
  - `mvn -q -o -pl litemall-order -am compile` clean.
  - Live: submit with a valid coupon reduces the order total and the coupon shows redeemed exactly once (resubmit/failure does not double-redeem; failed placement releases it); invalid coupon → 422 and no order row.
  - Domain code never imports the Feign client — only the facade; the client has timeouts + circuit-breaker.
  - Live: aftersale apply → admin approve → refund lands via the tender-parity path and the order timeline shows every hop; reject leaves the order refund-free; all customer reads/writes scope to `X-User-Id`.
  - Contract matches the promotion handoff spec (or the agreed stub), documented in `litemall-order/docs/`.

### Worktree: `gateway-api`
- **Branch:** `fix/gateway-api`
- **Path:** `../litemall-wt/gateway-api`
- **Scope:** `litemall-gateway-api/` only (gateway routes + customer SPA). Do not edit backend modules to paper over a gap — file a handoff note instead.
- **Context:** previous edge assignments merged. The SPA already ships Favorites, Footprint, Coupons, Feedback pages and a `/groupon` page — all guarded by `isMissingEndpoint` because the backends didn't exist. This wave they do (or will, per the handoff specs from `promotion`, `goods-management`, `order`).
- **Task:**
  1. **Routes:** `/srv/collect/**`, `/srv/footprint/**`, `/srv/feedback/**`, `/srv/comment/**` → `lb://litemall-goods-management`; `/srv/promotion/**` (and `/srv/coupon/**` if the promotion spec keeps that path) → `lb://litemall-promotion-service`; confirm `/srv/order/**` already covers the new aftersale paths. Machine-token relay must fire on each new route.
  2. **Wire the stub pages live** (remove the `isMissingEndpoint` guards as each endpoint lands): Favorites, Footprint, Feedback, Coupons (claim center + my-coupons per the promotion spec).
  3. **Coupon at checkout:** usable-coupon picker on the checkout view, `couponId` sent on submit, 422 coupon errors surfaced inline; order-confirmation shows the discount line.
  4. **Groupon flow:** `/groupon` page onto the live combination endpoints — browse active group-buys, start/join a group, my-groups state.
  5. **Review submission:** post-purchase comment form (star + text + images) on order detail / product detail, feeding `POST /srv/comment/post`.
- **Acceptance:**
  - `mvn -q -o -pl litemall-gateway-api -am compile` clean; SPA builds.
  - Live e2e through `:9000`/`:9001` → `:8090`: favorite a product from detail → appears on `/user/favorites`; footprint records on detail view; feedback submits; claim a coupon → select it at checkout → submitted order total reflects the discount; start/join a group on `/groupon`; post a review that then renders on the product page.
  - No `isMissingEndpoint` guard remains for an endpoint that is live; guards stay only for genuinely unshipped backends, each with a follow-up note.

### Worktree: `gateway-admin`
- **Branch:** `fix/gateway-admin`
- **Path:** `../litemall-wt/gateway-admin`
- **Scope:** `litemall-gateway-admin/` only (gateway routes + admin SPA). Same discipline: backend gaps become handoff notes, not edits to other modules.
- **Context:** previous edge assignments merged. The admin SPA already has Coupons/ and Groupons/ view files that are NOT routed (they fall through to the `NotAvailable` stub) and no promotion route exists. Recorded follow-ups that belong here: the loyalty controller 404 through the gateway, and `/srv/order/admin/stat`.
- **Task:**
  1. **Routes:** `/srv/promotion/**` → `lb://litemall-promotion-service` (admin coupon/groupon endpoints per the promotion handoff spec); verify the goods-management and order routes cover the new admin paths (`/srv/private/admin/{collect,footprint,feedback}`, `/srv/private/admin/aftersale`). Machine-token relay on each new route.
  2. **Route the existing Coupons/Groupons views** into `admin-routes.tsx` and bind them to the real promotion admin endpoints (coupon CRUD + issue records; group-buy rule CRUD + activity monitor).
  3. **New admin pages:** Feedback list, Collects list, Footprints list (goods-management admin endpoints), and an Aftersale queue (list + approve/reject) on order's admin aftersale endpoints — follow the existing UserList/AddressList page pattern.
  4. **Chase the recorded follow-ups:** loyalty controller 404 via the gateway (route or backend? diagnose, fix the gateway side, hand off the rest), and surface `/srv/order/admin/stat` if the order side ships it.
- **Acceptance:**
  - `mvn -q -o -pl litemall-gateway-admin -am compile` clean; admin SPA builds (prod webapp build stays green).
  - Live through `:18080`: coupon create → appears in the customer claim center; group-buy rule create → appears on the customer `/groupon` page; feedback/collect/footprint lists page real data; an aftersale application can be approved and the refund verified on the order.
  - No admin view routes to `NotAvailable` for a feature whose backend is live.