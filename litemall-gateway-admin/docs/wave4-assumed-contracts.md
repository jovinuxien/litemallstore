# Wave 4 — admin SPA surfaces built on ASSUMED order-service contracts

**From:** gateway-admin worktree, 2026-07-13.
**To:** order worktree (and whoever merges first).

At SPA build time the order worktree had committed **no** Wave-4 handoff docs
(checked `litemall-order/docs/` in the order worktree, main, and this
checkout; only the Wave-3 set exists and
`handoff-gateway-admin-cj-tracking.md` was not yet amended). The following
admin SPA surfaces were therefore built against the CLAUDE.md/plan contract
descriptions — the Wave-3 CJ-tracking-panel precedent. Every read path uses
tolerant field mapping concentrated in ONE api-slice file each, so contract
drift is a one-file fix per vertical.

| SPA surface | Endpoints assumed | Mapping lives in |
|---|---|---|
| Freight templates module + GoodsForm tempId dropdown | `/srv/private/admin/freight/{list,detail,create,update,delete,set-default,selectlist(BARE array),preview}` | `app/shared/reducers/private/services/adminFreightApi.ts` (TODO(freight-contract) markers) |
| Stores CRUD + write-off console | `/srv/private/admin/store/{list,read,create,update,delete}`; write-off preview `GET /srv/private/admin/order/writeoff?verifyCode=` + commit `POST` (three distinct 422 errmsgs shown verbatim) | `adminStoreApi.ts` |
| Order export button | `GET /srv/private/admin/order/export` + list filters + `start/end` | `downloadOrderExport()` in `adminOrderCjApi.ts` |
| Offline mark-paid | `POST /srv/private/admin/order/{id}/pay {reference?}`; confirm dialog NAMES the CJ createOrderV2 live replay for `source==='cj'` | `adminOrderCjApi.ts` |
| Dashboard channel/tender card | `GET /srv/private/admin/order/stat/channel?start=&end=` → `{bySource[],byTender[]}` | `adminOrderCjApi.ts` |
| Aftersale batch approve/reject | `POST /srv/private/admin/aftersale/batch-approve|batch-reject {ids}` → `{succeeded[],failed[{id,errmsg}]}` | `adminOrderCjApi.ts` |
| Tracking panel widening | `GET .../order/{id}/tracking` gains nullable `note`; local shipped orders trackable | `adminOrderCjApi.ts` (`ITracking.note`) |
| Print receipt + config | `POST .../order/{id}/print-receipt` (641 disabled / 642 failed), `GET .../order/fulfillment/config` | `adminOrderCjApi.ts` |
| Pickup surfaces | order list/detail DTOs gain `deliveryType`, `storeId`, `pickupName`, `pickupMobile`, `verifyCode`, `verifyTime`, `verifiedBy`; address convention `"PICKUP: <store name>"`; list filter `deliveryType=pickup` | `adminCatalogApi.ts` + `order.model.ts` |

Article/page/topic/history/comment-reply surfaces are NOT in this table —
they were built against goods-management's COMMITTED normative specs
(`spec-page-palette-v1.md`, `handoff-content-endpoints.md`).

**Ask:** when the order worktree commits its Wave-4 handoffs
(`handoff-admin-order-export-stat.md`, freight/store/write-off specs, amended
tracking handoff), diff them against this table; any drift lands in the named
api-slice file only. Post-merge live verification of these panels is a
recorded follow-up on the gateway-admin side.
