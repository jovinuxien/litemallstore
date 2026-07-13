# Handoff → gateway-admin: stores + pickup write-off (Wave 4)

Routes: `/srv/private/admin/store/**` and the existing `/srv/private/admin/order/**`
→ `lb://ORDER-SERVICE-APP` (machine token + ROLE_ADMIN at the edge, as today; the
write-off commit also wants `X-User-Id` relayed for the audit stamp).

## Store CRUD (`/srv/private/admin/store`)

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/list` | `{list: [store], total}` — includes hidden stores |
| GET | `/detail?id=` | full row; unknown → 422 |
| POST | `/create` | `{name*, intro, phone, address, detailedAddress, logo, latitude, longitude, businessHours, isShow}` → `{id}`; isShow defaults true |
| POST | `/update` | same + `id*` (full update — send every field; `isShow:false` hides the store from customers and 422s NEW pickup submits) |
| POST | `/delete` | `{id}` — logical; historical orders keep rendering |

Envelope: `{errno, errmsg, data}`; client errors HTTP 422 + `errno: 422`.

## Write-off (核销) — `/srv/private/admin/order/writeoff`

- **Preview (no state change):** `GET /srv/private/admin/order/writeoff?verifyCode=0123456789`
- **Commit:** `POST /srv/private/admin/order/writeoff` body `{"verifyCode": "0123456789"}`

Success `data` (both):

```json
{
  "id": 87, "orderSn": "2026...", "orderStatus": 201, "orderStatusText": "PAID",
  "deliveryType": "pickup", "consignee": "Anna Svensson", "mobile": "+4670...",
  "actualPrice": 42.50, "storeId": 3, "storeName": "Stockholm Central",
  "verifyTime": null, "verifiedBy": null,
  "items": [ {"goodsName": "Mug", "number": 2, "specifications": ["blue"]} ]
}
```

After commit: `orderStatus: 401`, `verifyTime`/`verifiedBy` set; the order timeline
gains a `writeoff` hop. SPA flow: scan → preview (show items + contact) → confirm →
commit.

Errors — HTTP 422, `errmsg` prefixed with a machine-branchable kind:

- `[UNKNOWN_CODE] No order carries this pickup code — check the scan and try again.`
- `[ALREADY_VERIFIED] This pickup code was already redeemed on <time> by <who>.`
  (also what a lost double-scan race returns; no state change)
- `[WRONG_STATE] Order <sn> is not a redeemable paid pickup order (status: <status>).`

## Order list/detail additions

Admin `GET /srv/private/admin/order/list` rows now carry `deliveryType` + `source` —
badge pickup orders and surface a "write-off" action for `deliveryType=pickup` +
`orderStatus=201`. Ship on a pickup order returns 422 (`This is an in-store pickup
order — redeem its verify code via write-off instead of shipping.`).
