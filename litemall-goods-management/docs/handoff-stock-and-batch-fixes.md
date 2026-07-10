# Handoff → `order` worktree: stock reservation is now real (+ read-contract fixes)

Closes every item in `litemall-order/docs/handoff-goods-management-defects.md`
(2026-06-19). Shipped from the `goods-management` worktree 2026-07-10.

## Defect 1 — `POST /srv/goods/stock/reduce` now actually decrements (CLOSED)

The no-op had two layers: the service body was commented out, **and** the
hand-written `GoodsProductMapper` interface in litemall-db had lost its mapper
XML (calling it threw `BindingException` — likely why it was commented out).
Both fixed:

- `litemall-db`: new `GoodsProductMapper.xml` binds `reduceStock`/`addStock`.
  The decrement is the atomic guarded UPDATE the defect report asked for:
  `UPDATE litemall_goods_product SET number = number - ? WHERE id = ? AND number >= ? AND deleted = 0`
  — check-and-decrement in one statement, so concurrent submits can never
  drive stock negative. **No schema migration** (plain DML mapper).
- Contract (unchanged request shape `{"productId": int, "number": int>=1}`):
  - success → `200 {"errno":0}` — exactly one row decremented.
  - insufficient stock / unknown / deleted product → `200 {"errno":631, "errmsg":"insufficient stock for product <id>"}`.
    HTTP stays 200 so Feign reads the failure from the body instead of an
    exception; your facade's `resp.getErrno() == 0` check already handles it —
    **no order-side change needed for reduce**.

## NEW — `POST /srv/goods/stock/restore` (wire `LitemallGoodsFacade.restoreStock` to this)

Inverse of reduce, for your rollback/cancellation compensation (today a logged
no-op on your side). Same request shape. Unguarded add
(`number = number + ?`, id must exist and not be deleted):

- success → `200 {"errno":0}`
- unknown/deleted product → `200 {"errno":632, "errmsg":"unknown or deleted product <id>"}`

It is NOT idempotent — one call restores once; keep your at-most-once
compensation semantics on the caller side.

## Defect 2a — `POST /srv/goods/batch` is now usable (CLOSED)

Response is keyed by the plain integer goods id
(`{"1181000": {...goods...}}`) instead of `LitemallGoodsId@3b488a9a` identity
strings. Your facade's per-id `goodsdetail` workaround can be retired when
convenient.

## Defect 2b — `GET /srv/goods/product` per-row `goodsId` fixed (CLOSED)

Each variant now carries the real parent goods id (was the row's own PK:
1, 2, 3…). Fixed in the repository converter both directions — the write path
(`addGoodsProduct`/`updateGoodsById`) was persisting the same wrong value into
`litemall_goods_product.goods_id`. Your facade's "stamp the authoritative
goodsId yourself" workaround is now redundant (harmless to keep).

## Also fixed while here

`verifyGoodsAvailability` (no live callers) had an inverted stream predicate —
it threw "insufficient stock" when any product HAD stock. Corrected; noted here
because it becomes safe to call if you ever adopt it.

## Deploy note (shared dev env)

The fix spans `litemall-db` (mapper XML) + `litemall-goods-management`. Going
live requires: `mvn install` litemall-db from the main checkout, then rebuild
and **restart goods-management** (stop before swapping the jar — the usual
NoClassDefFound-under-running-JVM trap). Order needs no rebuild for reduce;
rebuilding order only matters once you wire `restoreStock` to the new endpoint.
