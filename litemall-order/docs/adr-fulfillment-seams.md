# ADR: fulfillment seams — receipt printer + local express tracking (Wave 4, Task D)

Status: accepted, 2026-07-13. No DB migration.

## Decision: ports behind the facade convention, disabled by default

Two new ports live beside the module's other ACL facades in
`infrastructure/services/acl/facades/`:

- **`ReceiptPrinterPort`** — `print(ReceiptPrintJob) → OK|FAILED` (NEVER throws) +
  `enabled()`. Adapters: `LoggingReceiptPrinterAdapter` (provider `none`, default) and
  `YlyReceiptPrinterAdapter` over `YlyOpenApiClient` (provider `yly`).
- **`ExpressQueryPort`** — `query(carrier, trackNumber) → Optional<ExpressTrackingSnapshot>`
  (empty = miss/error, NEVER throws) + `enabled()`. Adapters: `NoopExpressQueryAdapter`
  (provider `none`, default), `KdniaoExpressQueryAdapter` (core `ExpressService`),
  `OnePassExpressQueryAdapter` (crmeb service hub). Real providers are wrapped in
  `CachingExpressQueryPort` (Caffeine, `litemall.order.express.cache-minutes` default 30,
  negatives cached too).

Provider selection is the promotion-service `StatsSourceConfiguration` pattern: ONE
factory `@Bean` per port in `FulfillmentSeamsConfiguration`, switching on
`litemall.order.printer.provider` / `litemall.order.express.provider`
(`FulfillmentProperties`). Adapters are plain classes constructed by the configuration —
not `@Component`s — so exactly one bean per port exists and injection points need no
qualifiers. Misconfigured real providers (yly without client-id/client-secret/machine-code;
onepass without account/secret) throw `IllegalStateException` at startup — fail fast, never
boot into silently-broken fulfillment. Secrets arrive via env vars (relaxed binding:
`LITEMALL_ORDER_PRINTER_CLIENT_SECRET`, `LITEMALL_ORDER_EXPRESS_ONEPASS_SECRET`, ...).

Both defaults are `none`: dev behavior is byte-identical to Wave 3 except (a) the receipt
now renders into the log after every payment and (b) local shipped tracking payloads gain
a nullable `note`.

## Yly (易联云) printer — auth/print shape

Open-platform v2, base `https://open-api.10ss.net/v2`, form-encoded POSTs, OkHttp directly
(no RestTemplate bean, no Feign):

- **Token** `POST /oauth/oauth`: `grant_type=client_credentials`, `client_id`,
  `timestamp` (epoch SECONDS), `sign = lowercase MD5(client_id + timestamp + client_secret)`,
  `scope=all`, `id=UUID` → `{error:"0", body:{access_token, expires_in}}`. Cached in
  Caffeine for `min(expires_in, 30 days)` (Yly tokens live ~35 days; 30 keeps margin).
- **Bind** `POST /printer/addprinter` (`machine_code`, `msign` + the common auth fields):
  LAZY, once per process before the first print (`AtomicBoolean`); "printer already added"
  error codes count as success; a transport failure re-arms the flag; any other rejection
  is logged and `/print/index` is left as the arbiter.
- **Print** `POST /print/index` (`access_token`, `client_id`, `machine_code`, `origin_id`,
  `content` + timestamp/sign/id) → `error:"0"` = accepted.

**`origin_id` exactly-once:** Yly dedupes on `origin_id`. Auto-print always sends the bare
`orderSn` — combined with the pay path's guarded 0-row UPDATE (the paid event fires at most
once per order), a receipt auto-prints exactly once even across retries. This fixes crmeb's
bug of hardcoding `origin_id = "order111"`, which collapses every order into one dedupe key.
The admin reprint sends `orderSn + "-R" + epochMillis` so a deliberate reprint is never
swallowed. **No per-order reprint cooldown is implemented yet** — a click-happy admin can
queue many physical prints; add a cooldown/audit if that becomes a problem.

The receipt template is ONE shared class (`ReceiptRenderer`, English, ~32-char monospace):
what the logging adapter logs is exactly what Yly prints. Yly accepts plain text (its
markup tags are optional), so no extra wrapping is applied.

## Express: why OnePass over kdniao

`KdniaoExpressQueryAdapter` exists and works, but two traps make it impractical here:

1. **Config precedence.** Core's `ExpressService` only calls kdniao when
   `litemall.express.enable=true` + appId/appKey — and those bind from litemall-core's
   profile yml, which OUTRANKS this module's `config/application.yml`. Setting
   `litemall.express.*` here does nothing; enabling kdniao requires env vars
   (`LITEMALL_EXPRESS_ENABLE=true`, `LITEMALL_EXPRESS_APP_ID`, `LITEMALL_EXPRESS_APP_KEY`)
   or editing core's yml.
2. **Chinese vendor names.** Core's vendor table maps CHINESE carrier display names to
   kdniao codes; the de-Chinesed dev DB ships English `ship_channel` values the table
   cannot resolve (the adapter falls back to treating the value as a code).

So **`onepass` is the practical provider**: crmeb's service hub (`https://sms.crmeb.net/api`),
login `POST /user/login` (account + secret — the configured secret is sent AS-IS; crmeb
hashes upstream at registration time, so configure whatever digest a crmeb install uses),
token cached ~2h, then `POST /v2/expr/query` form `{com, num}` with header
`Authorization: Bearer-<token>` (note the DASH — crmeb's convention).
**The OnePass call shape is from the crmeb reference implementation — live verification
requires real OnePass credentials.** Parsing is tolerant (`data.list[]` or `data[]`,
time/status field aliases) for that reason.

`OnePassTokenClient` is deliberately reusable: OnePass SMS is OUT of scope for Wave 4, but
a future `SmsSender` adapter should inject this same client (same account, same Bearer-
token) instead of logging in again.

## Auto-print: AFTER_COMMIT, dedicated executor, at-most-once

`OrderPaidReceiptPrintListener` is `@TransactionalEventListener(phase = AFTER_COMMIT,
fallbackExecution = false)` on `LitemallOrderPaidEvent`. Verified wiring: the pay path
(`LitemallOrderServiceImpl.markOrderPaid`, inside the orchestrator's `@Transactional`)
publishes through `LitemallSpringDomainEventPublisher` → Spring's
`ApplicationEventPublisher`, so the listener fires exactly when the payment commits and
never for a rollback. Delivery is **at-most-once by design**: the event itself fires at
most once (0-row-update pay guard), the print work is handed to a dedicated single daemon
thread (`receipt-print-*`, bounded queue 100, discard-oldest — under a printer outage the
newest receipts win and lost ones are reprintable from the admin surface), and the worker
catch-alls every throwable to a WARN. Payment can never block or fail because of printing.

## Tracking read

`CjTrackingService` → `OrderTrackingService` (`application/internal/` — it is order-wide
now). CJ branch byte-identical (including CJ-no-data → shipped-without-events, no note).
Local shipped orders consult `ExpressQueryPort`: hit → real events; provider disabled →
`note: "tracking provider disabled"`; miss/error → `note: "tracking temporarily
unavailable"`. `TrackingDtoResponse.note` is field-level `@JsonInclude(NON_NULL)` so every
pre-Wave-4 payload stays byte-identical. Contract: handoff-gateway-admin-cj-tracking.md.
