# Customer order list/detail — `/srv/order/list` + `/srv/order/detail`

Closes the two `litemall-order`-owned read endpoints the customer "My Orders"
SPA codes against (see `litemall-gateway-api/docs/SRV-FOLLOWUPS.md` → *Owner:
litemall-order*). Before this, `/srv/order/list` returned a **bare
`List<LitemallOrderAggregate>`** array and `/srv/order/detail` did not exist, so
the SPA's `unwrap<{list,total}>` got an array, `.list` was `undefined`, and the
page rendered empty.

## Contract (now implemented)

### `GET /srv/order/list?showType=&page=&limit=`
- Auth: buyer bound from the gateway-injected `X-User-Id` header (no caller
  `userId` param — same identity rule as submit/cancel/pay; no IDOR).
- `showType` → order_status bucket: `0`/absent = all, `1` = unpaid (101),
  `2` = to-ship (201), `3` = shipped (301), `4` = completed (401/402).
- Returns the order `ApiResponse` envelope `{ errno:0, data: { list, total } }`;
  the SPA `unwrap` strips the envelope to `{ list, total }`.
- Each `list` item (`OrderListItemDtoResponse`, ~`IOrderListItem`):
  `id, orderSn, actualPrice (number), orderStatusText, handleOption
  (cancel/pay/delete/refund/confirm/comment/rebuy/aftersale, from
  `LitemallOrderHandleOption.forStatus`), aftersaleStatus, goodsList[]`.
- `goodsList` item (`OrderGoodsDtoResponse`, ~`IOrderGoods`):
  `id, goodsId, goodsName, picUrl, number, price (number), specifications[]`.

### `GET /srv/order/detail?orderId=`
- Auth: `X-User-Id`; an order the caller does not own returns a 404 envelope
  (`getOrderForUser` filters by owner) — no cross-customer read.
- Returns `ApiResponse.ok(OrderDetailDtoResponse)` ~`IOrderDetail`:
  `id, orderSn, addTime (ISO), consignee, mobile, address, orderStatusText,
  handleOption, goodsPrice, freightPrice, couponPrice, actualPrice, orderGoods[]`.

## Implementation notes
- Prices serialise as plain numbers (`LitemallMoney.getAmount()`); the SPA reads
  them via `priceNum()` (tolerates `number` or `{amount}`).
- `total` comes from a new `LitemallOrderRepository.countByOrderStatus(userId,
  statuses)` (same criteria as the paged query). Order-goods lines come from
  `LitemallOrderGoodsRepository.findByOId(orderId)` via the orchestrator
  (`getOrderGoods`), N rows per page — acceptable for a 20-item page.
- Strategy unchanged: goods data for these *reads* comes from the order's own
  persisted `order_goods` rows, NOT a goods-management round-trip — consistent
  with the synchronous-Feign-for-placement decision in `adr-goods-acquisition.md`.

## gateway-api follow-ups (NOT done here — gateway-api worktree owns)
- Mark `/srv/order/list` + `/srv/order/detail` **LANDED** in
  `litemall-gateway-api/docs/SRV-FOLLOWUPS.md`.
- The customer-session-not-rehydrated-on-reload bug still bounces a hard reload
  of `/orders` to `/login` (recorded in SRV-FOLLOWUPS.md "Discovered during the
  order/user redesign"). Navigating in-app after login works; a reload needs the
  rehydrate fix.
- `modules/order/OrderList.tsx` action buttons call `confirm`/`refund`/`delete`
  verbs that are **not yet implemented** on the backend (only `cancel`/`pay`
  exist). Those POST actions are a deliberate next tranche, not part of view
  access. Until then those buttons no-op/error gracefully.
