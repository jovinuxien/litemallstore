# Handoff — Mail Outbox Admin Panel (Wave 6, Phase C)

Contract for **gateway-admin**'s "Mail outbox" page, backed by the order
service's `/srv/private/admin/mail/**` endpoints (V41 `litemall_mail_outbox`).
Same trust model as the other order admin prefixes: the admin edge gates the
route to ROLE_ADMIN and relays the machine token; the order service adds no
auth of its own. Route through the existing explicit order admin route
(`/srv/private/admin/**` → order service) — verify `/srv/private/admin/mail/**`
matches it; add the prefix if the edge enumerates them.

## What produces rows

`CustomerMailEnqueueListener` (AFTER_COMMIT on committed order transitions),
only when customer mail is **enabled**:

| Trigger                     | template_key         | Extra content                    |
|-----------------------------|----------------------|----------------------------------|
| Payment commits             | `order-confirmation` | order sn, total                  |
| Pickup order payment commits| `pickup-code`        | store name, 10-digit verify code (in addition to order-confirmation) |
| Order ships                 | `shipped`            | carrier (`ship_channel`), tracking (`ship_sn`) |
| Aftersale approved          | `refund-approved`    | refunded amount                  |

`password-reset` is rendered by **gateway-api**'s edge-local reset sender and
never appears in this outbox (edge sends synchronously, no outbox row).

Buyers without an email (`litemall_user.email` NULL/blank) are silently
skipped — no row. Rows are enqueued with `send_at = now`; a **future**
`send_at` is honored (the row stays invisible to the sweep until then) — the
scheduled-send seam for future writers.

Delivery: `MailOutboxSweepScheduler` (default every 60s, batch 50) sends via
litemall-core's `CustomerMailSender`. Failures record `last_error` and bump
`attempts`; the 5th failed attempt flips the row to `failed` (terminal until
resend).

## Endpoints

### `GET /srv/private/admin/mail/list?status=&page=1&limit=10`

- `status` (optional): `pending` | `sent` | `failed`. Anything else → errno 402
  (bad argument value). Empty/absent = all.
- `limit` is capped at 100.

Standard envelope, `data`:

```json
{
  "list": [
    {
      "id": 7,
      "recipient": "buyer@example.com",
      "subject": "Your litemall order 20260716123456 is confirmed",
      "body": "Thank you for your purchase!\n\n…",
      "templateKey": "order-confirmation",
      "status": "pending",
      "attempts": 0,
      "sendAt": "2026-07-16T12:34:56",
      "lastError": null,
      "addTime": "2026-07-16T12:34:56",
      "updateTime": "2026-07-16T12:34:56",
      "deleted": false
    }
  ],
  "total": 1, "page": 1, "limit": 10, "pages": 1
}
```

Notes for the SPA:
- Newest first (`id desc`).
- `status` chips: `pending` / `sent` / `failed`; show `lastError` as a tooltip
  on failed (and on pending rows with `attempts > 0` — a retry in progress).
- `attempts` column: 5 = the failure cap.
- `body` is plain text (v1) — render in a `<pre>`/monospace preview if shown.
- Timestamps are LocalDateTime JSON (same shape as the other order admin
  surfaces — beware the promotion-service array format does NOT apply here).

### `POST /srv/private/admin/mail/{id}/resend`

Guarded reset of a **failed** row: back to `pending`, `attempts = 0`,
`last_error` cleared, `send_at = now` (next sweep delivers). Success returns
the refreshed row in `data`. Row missing or not `failed` → HTTP 422 with
`{"errno": 422, "errmsg": "Only failed outbox rows can be resent"}` — show the
Resend button on failed rows only.

## Config keys (`litemall.customer-mail.*`)

Shared namespace — the SAME keys drive core's SMTP sender (order sweep) and
gateway-api's edge-local `ResetMailSender` (its own JavaMailSender, no code
dependency on core needed edge-side).

| Key | Default | Meaning |
|-----|---------|---------|
| `litemall.customer-mail.enabled`  | `false` | Master switch. Disabled = no rows enqueued, sweep skips, no-op sender bean — byte-identical boot. |
| `litemall.customer-mail.from`     | `noreply@litemall.dev` | From address. |
| `litemall.customer-mail.host`     | `localhost` | SMTP host (dev default = MailHog). |
| `litemall.customer-mail.port`     | `1025` | SMTP port (MailHog). |
| `litemall.customer-mail.username` | *(blank)* | Blank = no SMTP auth (MailHog); non-blank also enables STARTTLS. |
| `litemall.customer-mail.password` | *(blank)* | |
| `litemall.customer-mail.sweep-ms` | `60000` | Sweep cadence (order service only). |
| `litemall.customer-mail.sweep-batch` | `50` | Rows per sweep (order service only). |

**Enable via ENV VARS** (`LITEMALL_CUSTOMERMAIL_ENABLED=true`, etc.):
litemall-core's profile yml outranks service yml, so env is the only override
that always wins (the kdniao precedence lesson).

Dev SMTP: MailHog — `docker run -p 1025:1025 -p 8025:8025 mailhog/mailhog`
(or promotion's `docker-compose.marketing.yml`); UI at `:8025`.

## Template keys (cross-service contract — do not rename)

`order-confirmation` · `shipped` · `refund-approved` · `pickup-code` ·
`password-reset` (constants in litemall-core `MailTemplates`).
