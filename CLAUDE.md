# CLAUDE.md — litemall Per-Worktree Tasks

## Per-worktree tasks

> Active fix branches launched via `open-fix-worktrees.sh`. Each Claude session
> opens in `../litemall-wt/<short>` on branch `fix/<short>` and is told to read
> the matching `### Worktree: <short>` block below as its sole task. Edit the
> Task / Acceptance lines to redirect a worktree.
>
> **History:** Wave 2 (coupons, groupon, aftersale, engagement), Wave 3
> (CJ API parity, createOrderV2 lifecycle, tracking, admin panels), Wave 4
> (legacy wx-api/admin-api decommission + crmeb verticals + account
> self-service), the goods deals wave (flash-deal lifecycle + related items),
> Wave 5 (affiliate program: brokerage engine V39, invite capture, affiliate
> portal + admin-edge auth split), and Wave 6 (social promotion V42 +
> Meta/TikTok ACLs + admin composer, order mail outbox V41 + core `mail`,
> Matomo tracker + SPA reset mail, admin social/mail panels) are fully merged
> to master; their specs live in git history.
>
> **Wave 7 (2026-07-16) — PRODUCTION READINESS. This wave is different from
> every prior wave: it ships almost no features. It closes the gap between
> "works on my machine" and "can take a stranger's money."** Plan:
> `doc/production-readiness-plan-2026-07-16.md` (six-agent audit + locked
> decisions; approved by the user 2026-07-16 — implement directly, no
> per-worktree re-approval unless deviating).
>
> **Locked:** market = **US and EU** (⇒ sales tax AND VAT/OSS at checkout,
> cookie consent + privacy policy are LEGAL prerequisites, not polish);
> deploy = **single VPS via docker-compose** (⇒ 1 replica/service, which is
> what makes the unguarded @Scheduled jobs survivable — see `platform` Task
> E); payments = **Stripe only** (delete the dead PayPal/Alipay/WeChat/
> Apple/Google enums); scope = **minimum safe launch** (the debt register at
> the bottom of the plan records what was cut and why — read it before
> proposing extra work).
>
> **🔴 THE HEADLINE — CORRECTED 2026-07-16, and now FIXED (`order`, merged).**
> The audit claimed `POST /srv/cart/items {"price":0.01}` + submit = a real
> 1-cent order. **It never did.** `validateProductStock():49-55` already
> re-stamped the catalog price onto every checked line, 11 lines before
> `priceCalculation()` — the order total was always server-derived. The audit
> traced the cart-write→total path but stopped short of the re-stamp, which
> hides inside a method named for a stock check.
> **What was real, and is now closed by Task E0:** the cart IDOR (all six
> `/items` endpoints took `userId` from a param/body), `goodsSn`/`goodsName`/
> `picUrl` injected verbatim into permanent `order_goods` rows, a null catalog
> price falling through to the client's value, and the tampered price still
> being what the cart page *displayed* (display vs charge divergence).
> Consequence: E0 did NOT gate Stripe, and the two shipped independently.
> Full write-up: `litemall-order/docs/adr-stripe-payments.md` §1.
>
> **USER-SIDE PREREQUISITES (block launch, not development): Stripe live keys
> + webhook secret, Stripe Tax registration (US nexus states + EU OSS),
> domain + DNS, real SMTP creds, the VPS. Everything must build, boot, and be
> verifiable WITHOUT them** — adapters disabled by default, honest typed
> errors, never a 5xx, never a fake success. **One exception to the Wave-6
> fail-soft habit: TAX FAILS CLOSED.** A tax outage blocks checkout; it never
> ships an untaxed order.
>
> **Dependency order:** `platform` starts NOW and is independent — its TLS +
> domain work gates the Stripe webhook, so it is the real critical path
> despite feeling least urgent. `order` and `gateway-api` start together
> (agree the PaymentIntent-creation contract + publishable-key seam on day
> one; `order` commits `docs/handoff-stripe-checkout.md`). **`order` and
> `gateway-api` MERGE TOGETHER OR NOT AT ALL** — verified payments behind an
> unauthenticated edge is still exploitable, and enforced auth without real
> payments strands the funnel. `promotion` and `gateway-admin` have NO Wave-7
> assignment. `goods-management` stays PARKED — see its block.
>
> **Verification is inverted this wave.** Prior waves demoed a feature
> working. Here the deliverable is the ABSENCE of a vulnerability, and
> absence does not demo. Every security acceptance is written as an
> ADVERSARIAL check — send the exploit, prove it fails. The five that are
> live today and must each be dead: cart price tampering; junk
> `paymentIntentId` → paid; anonymous cart read/write by `userId`; PaymentIntent
> replay across orders; forged webhook signature.
>
> **Cross-cutting landmines (apply to every block):**
> - **Flyway:** V42 is the last used (Wave-6 social_post). **V40 remains
>   EARMARKED** for goods-management's parked CJ-deals SKU-charge fix — do NOT
>   take it. Wave-7 migrations claim **V43+** after checking
>   `flyway_schema_history` immediately before first boot. `out-of-order: true`
>   is permanent. Never `flyway repair`.
> - **litemall-db is shared and hand-maintained:** never regenerate; hand-edit
>   entities + mapper XMLs together (the `now_money` silent failures). After
>   editing: `mvn install` litemall-db, restart EVERY dependent, verify the
>   nested `BOOT-INF/lib` copy in running exec jars; concurrent `-am` builds
>   overwrite `~/.m2`.
> - **`andLogicalDeleted()` is INVERTED across 33 domain classes** — the
>   generator's `Boolean.valueOf("0")`/`("1")` makes BOTH enum constants
>   `false`, so `andLogicalDeleted(false)` returns only DELETED rows. Dormant
>   only because zero call sites exist. **Do not call it.** Bind the literal,
>   as `LitemallAftersaleRepositoryImpl:45-60` already does.
> - **litemall-core is shared:** install/restart-all-dependents discipline;
>   enable core-read config via ENV VARS (the kdniao precedence trap — core
>   profile yml outranks service yml).
> - **svcsecurity is deny-by-default** — but note the Stripe webhook MUST be
>   anonymous (Stripe cannot present a machine token). It is signature-gated
>   instead. That is the ONLY new anonymous path this wave.
> - **Never rebuild a jar under a running JVM** (hung statics). Kill first.
> - Verify live through the gateways (`:9000`/`:9001`→`:8090`, `:18080`) —
>   machine-token ~10-min TTL makes direct service curls flaky.

### Worktree: `goods-management`
- **PARKED — no Wave-7 assignment.** Carries the approved-but-parked CJ-deals
  work (design `doc/cj-deals-strategy-2026-07-16.pdf`; reference impl in
  reverted commit `a82a19e0e`; **V40 stays earmarked for it**). The user parked
  it 2026-07-16 pending a redesign for efficiency. The SKU-level swap-charge fix
  from that wave is KEPT and already on master. Do not resume without a new
  instruction.
- **Wave-7 note (informational, no action):** `order` Task E0 makes cart pricing
  server-authoritative. Flash-deal prices must then resolve server-side from the
  deals tables rather than arriving on the cart line. If E0 surfaces a deal-price
  regression, `order` files a handoff here — it does NOT edit goods-management.

### Worktree: `order`
- **Branch:** `fix/order` — FIRST: `git merge master`. · **Scope:**
  `litemall-order/` + the migration it owns (**V43+**). Owns the money path end
  to end. Full spec: `doc/production-readiness-plan-2026-07-16.md` §Worktree
  `order` — read it; the summary below is not a substitute.
- **Task E0 — server-authoritative pricing. DO THIS FIRST, BEFORE STRIPE.**
  Cart-add must resolve price from the catalog via `goodsFacade` (copy
  `addToCart():697-723`), and **`price` must be DELETED from
  `AddCartItemRequest`** — not merely ignored; a field that looks authoritative
  and isn't will be re-trusted by the next reader. Same for `goodsSn`/
  `goodsName`/`picUrl` (they reach `order_goods` rows via
  `LitemallOrderServiceImpl:410-416`). Re-validate the subtotal against live
  catalog prices at submit → mismatch = clean 422 ("prices changed, review your
  cart"), never a silent recompute. SPA-side: stop sending `price` in the mirror
  (`orderSlice.ts:158-168`); delete the never-dispatched `remoteAddToCartThunk`.
  **Watch the flash-deal regression** (deal price must survive server-side
  resolution).
- **Task A — real Stripe verification.** `processPayment():494-510` accepts any
  non-blank string today. `PaymentIntent.retrieve` + assert ALL of: status
  `succeeded`, `amount_received` == order total (minor units, no float),
  currency, `metadata.orderId`. Mismatch → reject, order stays CREATED, no CJ
  placement. Fail CLOSED. UNIQUE index on the payment reference (replay across
  orders → reject). **Webhook** `POST /srv/stripe/webhook/order-paid`:
  `Webhook.constructEvent` signature verification, ANONYMOUS in svcsecurity but
  signature-gated, idempotent on `event.id`, handles `payment_intent.succeeded`/
  `.payment_failed`. The webhook — not the client call — is the authoritative
  paid signal. Config `litemall.stripe.*`, `enabled:false`, keys via ENV with NO
  committed fallback (copy `promotion/application.yml:58`'s `${VAR:}` pattern).
  Delete the dead PayPal/Alipay/WeChat/Apple/Google enum values.
- **Task B — real refunds.** `settleRefundToTender():1052-1058` only logs
  "would reverse". Implement `Refund.create`; on API failure the order must NOT
  flip to REFUNDED — surface to admin, keep it retryable.
- **Task C — tax (US + EU).** Migration: `tax_price` on `litemall_order`
  (+ `tax_breakdown` JSON for invoices/audit). `TaxCalculationPort` in
  `application/ports` + `infrastructure/acl/stripe/StripeTaxAdapter` (domain
  never imports the client). **FAILS CLOSED** — disabled or unreachable blocks
  checkout. Keep IOSS (`CjDropshipOrderFacadeImpl:54-61`) consistent with VAT
  collected, or EU buyers pay twice at customs.
- **Task D — server-authoritative totals.** Implement `GET /srv/cart/checkout`
  (goods total, freight, tax, discounts, grand total). `cartApi.ts:43` already
  calls it; it has never existed and the SPA falls back to a client-side total.
  With tax this is mandatory — the client must never compute money.
- **Task E — cart identity + broken list + order_sn.** Take identity from
  `@RequestHeader("X-User-Id")`, never `@RequestParam`/body
  (`LitemallCartController:32,38,44,51,62,68`; the legacy methods at :82-103
  already do it right). This also fixes `GET /srv/cart/items`, which **400s on
  every logged-in fetch today** (client sends no `userId`; the param is
  required). Migration: UNIQUE on `order_sn` + index on `litemall_order.user_id`
  and `litemall_cart.user_id`; `generateOrderSn():166-176` checks uniqueness
  PER USER while reconciliation is global. Move
  `cjFulfillmentService.placeForPaidOrder` (:333) OUT of the class-level
  `@Transactional` (sync Feign inside a money TX holds row locks + a pooled
  connection) — use the module's proven AFTER_COMMIT + outbox pattern.
- **Acceptance:** compile clean; boots with Stripe disabled (card pay → typed
  error, NOT a fake success); migration applies. **Adversarial, all must be
  dead:** `POST /srv/cart/items {"price":0.01}` → line persists at CATALOG
  price and the order charges full price; `POST /srv/order/{id}/actions/pay`
  `{"paymentIntentId":"x"}` → rejected; PaymentIntent replayed on a 2nd order →
  rejected; tampered amount → rejected; forged webhook signature → 400, order
  untouched; webhook replay of the same `event.id` → no double-processing.
  `GET /srv/cart/items` returns the server cart (today: 400). Refund visible in
  the Stripe dashboard; forced API failure → order does NOT show REFUNDED. US
  and EU addresses each produce a correct server-computed total; tax provider
  down → checkout BLOCKED. Flash-deal line still charges the deal price.
  Wave-5/6 wallet/brokerage/aftersale/CJ/mail regression green.

### Worktree: `gateway-api`
- **Branch:** `fix/gateway-api` — FIRST: `git merge master`. · **Scope:**
  `litemall-gateway-api/` (edge + customer SPA). NO migration. Full spec:
  `doc/production-readiness-plan-2026-07-16.md` §Worktree `gateway-api`.
- **Task A — actually enforce authentication.** `SecurityConfig.java:29` is
  `anyExchange().permitAll()` and `IdentityForwardingFilter:28-31` admits
  authorization "is deliberately not enforced here yet". Because
  `MachineTokenRelayFilter:44-62` attaches a machine token to EVERY `lb://`
  route regardless of caller, downstream `authenticated()` passes for anonymous
  requests. Enumerate public paths explicitly (catalog, search, product detail,
  `/auth/**`, health, **the Stripe webhook**) and require an authenticated
  customer for everything else (`/srv/cart/**`, `/srv/order/**`, `/srv/user/**`).
  **Only relay the machine token for verified-identity or public requests** —
  the relay is what makes the missing gate exploitable; close both.
  **RESOLVED, no guest-cart complication:** the guest cart is sessionStorage-only
  and `fetchCart` short-circuits when logged out (`cartSlice.ts:33-35`), so
  gating `/srv/cart/**` costs nothing. Regression: anonymous browse/search/PDP
  and the `?invite=` + `utm_*` capture must still work.
- **Task B — real Stripe Elements.** `orderSlice.ts:228` fabricates
  `pi_stub_${orderId}` and `Checkout.tsx:731` DISCLOSES the placeholder to the
  customer. Create the PaymentIntent server-side, confirm client-side, send the
  real id; delete the stub path and the disclosure copy. Publishable key via the
  Wave-6 `/auth/site-config` seam — absent ⇒ card payment cleanly unavailable,
  never stubbed.
- **Task C — cookie consent (LEGAL BLOCKER).** Matomo is live
  (`shared/tracking/matomo.ts`) with NO consent UI. Opt-in banner: no tracker
  injection before affirmative consent, persisted choice, withdrawal path; keep
  the existing DNT hard opt-out. Use Matomo's `requireConsent`/`disableCookies`.
- **Task D — legal pages (LEGAL BLOCKER).** `Layout.tsx:407-423` renders
  "Conditions of Use", "Privacy Notice", "Cookie Preferences", "Contact Us" —
  **all four route to `/help` or `/service`**. They are decorative. Add real
  routed pages: Terms of Sale, Privacy Policy (must name Matomo, Stripe, CJ,
  Mautic as processors), Cookie Policy (wired to Task C), Returns/Refund policy.
  **Copy is a legal deliverable, not an engineering one — if no counsel-reviewed
  text exists, FLAG IT; do not invent legal text.**
- **Task E — turn the mail on.** `application.yml:114-118` `reset-mail.enabled:
  false` makes `ResetPassword.tsx:13-17` HIDE the forgot-password tab entirely —
  a stock deploy locks out anyone who forgets their password. Enable in the prod
  profile (needs SMTP); verify the tab appears and the token round-trips.
- **Acceptance:** compile + SPA build clean. **Adversarial:** anonymous
  `GET /srv/cart/items?userId=2` → 401; authenticated as user 1 requesting user
  2's cart → 401/403 (both exploitable today). Anonymous browse/search/PDP still
  200. `?invite=` + `utm_*` on a product URL → both captured, routing normal.
  Real Elements payment completes; no `pi_stub_` string in the bundle. Fresh
  visitor → NO Matomo cookie and NO tracker request before consent; accept →
  tracking starts; withdraw → stops; DNT still hard-opts-out. All four footer
  links reach real distinct pages. Reset: tab visible, mail arrives, token
  round-trips.

### Worktree: `platform` (NEW)
- **Branch:** `fix/platform` — FIRST: `git merge master`. · **Scope:**
  `docker/`, `docker-compose/`, `.github/workflows/`, `deploy/`, and per-service
  **`application-prod.yml` (CREATES prod profiles only — does NOT touch default
  yml**, so it cannot conflict with `order`/`gateway-api`). Full spec:
  `doc/production-readiness-plan-2026-07-16.md` §Worktree `platform`.
  **This worktree exists because nothing currently deploys this application.**
- **Task A — containerize the real architecture.** The only Dockerfile packages
  the DEPRECATED monolith on EOL `openjdk:8-jre`; `docker-compose-recover.yml`
  references `litemall/*` images no script ever builds; `deploy/bin/deploy.sh`
  is `nohup java -jar` on a hardcoded `/home/ubuntu` path. Write a JDK-21
  Dockerfile per service (9), multi-stage, non-root, `-XX:MaxRAMPercentage`.
  `docker-compose.prod.yml`: 9 services + MySQL + ES + Kafka, `restart:
  unless-stopped`, memory limits, **healthchecks** (none exist for any litemall
  service today). DELETE or fix `docker-compose-recover.yml` and `deploy/` —
  they describe an architecture that no longer exists. Fix `dc-local.sh:7` /
  `dc-cloudconfig.sh:7`, which reference **`docker-compose.base.yml` — a file
  that does not exist** (the documented local bring-up is broken).
- **Task B — prod profiles + secrets out of the repo.** No `application-prod.yml`
  exists for ANY service; all ship `active: dev` with DEBUG logging. Real prod
  profile per service (INFO, no dev seed, prod pool sizing). Every secret via ENV
  with **no committed fallback** (`${VAR:}` pattern). Covers: DB password
  (`Calliste_1006`, 6+ files), CJ API keys (**TWO different values committed —
  resolve the sprawl**), Tencent COS, WeChat, Stripe. **Fail startup in prod when
  a JWT key is absent** instead of `RsaKeys.from():49-63` silently generating an
  ephemeral per-boot pair (`JwtService.java:51-56` only WARNs). Rotate the
  committed RSA keys for both realms (`gateway-api/application.yml:155-183`,
  `gateway-admin/bootstrap.yml:48-76`) and the machine-client dev secrets
  (`AuthServerProps.java:16,19`). **Lock down `litemall-config`:**
  `application.yml:3-11` exposes `include: "*"` PLUS `env.post.enabled: true`
  with NO Spring Security in the module — a writable, unauthenticated
  `/actuator/env`. Untrack `backup/*.sql` + `litemall-all/backup/*.sql` (full
  dumps incl. the bcrypt admin hash); add a generic `.env*` gitignore rule (only
  `.env.marketing` is covered). Point the config server away from the PUBLIC
  `jovinuxien/litemall-config` (it holds no secrets today — all files 0 bytes
  bar a stale dev yml routing to Wave-4-deleted `wx-api`/`admin-api` — but its
  empty `application-prod.yml` files are a trap: filling them in PUBLISHES prod
  config).
- **Task C — TLS + ingress.** No TLS config exists anywhere; the only nginx
  sample (`doc/conf/nginx.conf`) is a 2018 relic with deprecated TLSv1/1.1
  proxying to the dead monolith. Caddy (or nginx + certbot) terminating TLS for
  both gateways, HSTS, HTTP→HTTPS, real CORS origins (hardcoded
  `http://localhost:9000` today, `application.yml:22-24`). **Gates `order` Task
  A's webhook** — Stripe needs a public HTTPS URL.
- **Task D — CI that actually runs.** `.github/workflows/main.yml` builds JDK
  8/11 (root pom needs **21**) and npm-tests `litemall-admin`/`litemall-vue`,
  **deleted in Wave 4** — nothing has been verified automatically in months.
  Rewrite for JDK 21 + the real reactor + both SPAs. **Make the tests RUN:** root
  `pom.xml:45` sets `maven.test.skip=true` reactor-wide and
  `litemall-db/pom.xml:218`'s `skipTests=false` does NOT override it
  (`maven.test.skip` is the master switch and wins). Fix or quarantine
  `MapperReturnTest` (breaks test compilation). Add gitleaks on PRs.
- **Task E — enforce the 1-replica constraint.** Single-VPS makes the unguarded
  schedulers safe, but NOTHING enforces it. No ShedLock/leader election exists:
  mail sweep, CJ status sync (per-replica external API budget), catalog refresh,
  `SocialDealAutoPoster` (would double-post to Meta/TikTok), brokerage unfreeze,
  outbox relay. **Pin `replicas: 1` in compose with a comment pointing at this
  constraint**, and add it to the plan's debt register. The trap is a future
  operator scaling out and silently double-charging the CJ API.
- **Acceptance:** `docker compose -f docker-compose.prod.yml up` brings all 9
  services healthy from a clean checkout on a fresh host with **zero secrets in
  the repo** — every credential from env/`.env` (gitignored). Boot with a
  required secret missing → **fails fast with a clear message**, does not
  silently generate an ephemeral key. `curl -X POST
  https://<host>/actuator/env` on the config server → 404/401, not 200. TLS:
  A-grade handshake on both gateways; HTTP redirects; Stripe reaches the webhook
  over public HTTPS. CI green on push and **actually running tests** (break one
  deliberately → CI red). gitleaks clean.

### Worktree: `promotion`
- **No Wave-7 assignment.** Wave-6 social vertical + marketing infra are merged
  (`5d21fe11f`). Do not start work here without a new instruction.
- **Informational:** `platform` Task E pins replicas to 1 partly because
  `SocialDealAutoPoster` has no leader election and would double-post to
  Meta/TikTok. If auto-post is ever wanted at scale, ShedLock is the
  prerequisite (recorded in the plan's debt register).

### Worktree: `gateway-admin`
- **No Wave-7 assignment.** Wave-6 social/mail panels are merged (`cca573cf6`).
  Do not start work here without a new instruction.
- **Informational:** admin RBAC is FLAT (`AuthController.java:59,88` hardcodes
  ROLE_ADMIN for every admin login), which the plan's debt register knowingly
  defers for a small trusted admin team. It bites on the first non-founder admin
  hire — a support agent can currently drain wallets via
  `LitemallAdminExtractController`. Promote to a wave when the team grows.
