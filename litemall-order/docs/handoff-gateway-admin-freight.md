# Handoff → gateway-admin: freight-template admin surface (Wave 4)

Route: `/srv/private/admin/freight/**` → `lb://ORDER-SERVICE-APP` (same relay rules as
`/srv/private/admin/order/**`: machine token + admin-gated at the edge). Envelope:
`ResponseUtil` `{errno, errmsg, data}`; hard client errors are HTTP 422 with
`errno: 422` and a human `errmsg`.

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/list` | `{list, total}`; rows: `{id, name, type, appoint, isDefault, sort, addTime, updateTime, regionCount, freeRuleCount}` |
| GET | `/selectlist` | **BARE array** `[{id, name, isDefault}]` (dropdown convention) |
| GET | `/detail?id=` | `{template, regions[], freeRules[]}`; unknown id → 422 |
| POST | `/create` | body below; returns `{id}` |
| POST | `/update` | same body + `id`; child rows replaced wholesale; unknown id → 422 |
| POST | `/delete` | `{id}`; **422 while a live (on-sale) goods references it** |
| POST | `/set-default` | `{id}` (0 clears); the default template prices goods with `temp_id=0` |
| GET | `/preview?tempId=&quantity=&amount=&countryCode=&provinceName=` | dry-run one template: `{templateId, templateName, source, amount, note}` |

## Create/update body

```json
{
  "id": 3,                            // update only
  "name": "EU parcels",
  "type": 1,                          // 1=per piece (2/3 accepted, billed per piece v1)
  "appoint": 1,                       // 0=no free rules, 1/2=free rules active
  "sort": 0,
  "regions": [
    { "countryCode": "SE", "provinceName": null,
      "first": 1, "firstPrice": 5.00, "continueP": 1, "continuePrice": 3.00 },
    { "countryCode": "*",  "provinceName": null,
      "first": 1, "firstPrice": 9.00, "continueP": 1, "continuePrice": 5.00 }
  ],
  "freeRules": [
    { "countryCode": "SE", "provinceName": null, "number": 5, "price": 60.00 }
  ]
}
```

- `countryCode` `'*'` = any destination (default when omitted); `provinceName` free
  text, matched case-insensitively against the customer's address province; `null` =
  whole country. Specificity: (country, province) → (country, null) → `'*'`.
- Free rule: group free when `units >= number` OR `amount >= price`; 0 = unused
  dimension.
- Template edits price live within 5 minutes at worst (server cache) — immediately
  after any mutation through these endpoints (cache invalidated).
- Goods are bound to templates via `litemall_goods.temp_id` — the binding UI belongs
  to goods-management admin (see `handoff-goods-tempid-binding.md`); this surface only
  manages the templates themselves. `/selectlist` exists to feed that binding UI too.
