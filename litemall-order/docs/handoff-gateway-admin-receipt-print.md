# Handoff: admin receipt reprint + fulfillment config (gateway-admin)

Wave 4, order Task D. Both endpoints sit under the already-routed
`/srv/private/admin/order/**` prefix (machine token + `X-User-Roles: ROLE_ADMIN` relay as
usual; the controller adds no auth of its own). Envelope: litemall-core `ResponseUtil`
(`{errno, errmsg, data}`).

## `POST /srv/private/admin/order/{orderId}/print-receipt`

Reprint the receipt of a PAID order on the configured cloud printer. No request body.

| Outcome | HTTP | errno | errmsg | SPA treatment |
|---|---|---|---|---|
| printed / accepted by the vendor | 200 | 0 | 成功 | toast "receipt sent to printer" |
| unknown orderId | 200 | 402 | (badArgumentValue) | not found |
| order not paid yet, or cancelled | 422 | 422 | `only paid orders have receipts` | disable/explain |
| printer provider is `none` | 200 | **641** | `receipt printer disabled` | grey the button out (see config endpoint — don't render the button at all when `printer.enabled` is false) |
| vendor rejected / unreachable | 200 | **642** | `print failed` | error toast, retry allowed |

Semantics worth knowing:

- The **auto-print** already happens server-side after every successful payment (when
  `printer.auto-print` is on); this endpoint is for REPRINTS (paper jam, customer copy).
- Exactly-once: the auto-print uses `origin_id = orderSn` (vendor-side dedupe); each
  reprint uses `orderSn + "-R" + epochMillis`, so every reprint click prints again —
  there is NO per-order cooldown yet. Consider a confirm dialog.

## `GET /srv/private/admin/order/fulfillment/config`

Flags only — never secrets. Literal path segment (like `cj/balance`, `stat/channel`).

```json
{
  "errno": 0, "errmsg": "成功",
  "data": {
    "printer": { "provider": "none", "enabled": false, "autoPrint": true, "businessName": "litemall" },
    "express": { "provider": "none", "enabled": false, "cacheMinutes": 30 }
  }
}
```

- `printer.enabled` — a REAL printer is configured (`provider: yly`); gate the reprint
  button on this. `provider: none` still auto-"prints" to the service log in dev.
- `express.enabled` — live local-carrier tracking is configured (`kdniao`/`onepass`);
  when false, local orders' tracking payloads carry
  `note: "tracking provider disabled"` (see handoff-gateway-admin-cj-tracking.md).
