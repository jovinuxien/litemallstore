# Handoff → `goods-management` worktree: defects blocking order placement

Found 2026-06-19 while wiring the order→goods integration for `POST /srv/order/submit`
(see `litemall-order` commit `b87474105`). Order placement now works end-to-end (a real
order persists), but the order side had to **work around** goods-management quirks, and one
defect it **cannot** work around (stock is never reserved). These live in
`litemall-goods-management/` and are out of scope for the `order` worktree — please fix
them here.

All evidence below was captured live against goods-management on `:8082` with a valid
`client_credentials` machine token (client `gateway-api`, authserver `:8089`).

---

## Defect 1 — `reduceStock` is a no-op (CRITICAL: stock is never decremented)

`litemall-goods-management/.../application/LitemallGoodsManagementServiceImpl.java`, method
`reduceStock(LitemallGoodsProductId, Short)` (~line 149): **both** implementation lines are
commented out, so the method does nothing but still returns success.

```java
public void reduceStock(LitemallGoodsProductId productId, Short number) {
   //goodsProductRepository.reduceStock(productId, number);
   //goodsProductRepository.reduceStock(productId, number);
}
```

**Evidence**
```
POST /srv/goods/stock/reduce  {"productId":1,"number":1}
→ 200  {"errno":0,"data":null,"errmsg":"success"}
litemall_goods_product.number for product 1: 100 before AND 100 after.
```
Placing an order (product 1, qty 1) returns HTTP 201 and persists the order, but
`litemall_goods_product.number` stays 100 — **stock is not reserved → oversell**.

**Impact** — Orders commit without decrementing stock. The order service correctly calls
`POST /srv/goods/stock/reduce` per line and treats `errno 0` as confirmed; goods-management
simply doesn't honor it. The order side's compensating release
(`LitemallGoodsFacade.restoreStock`) is therefore also moot (and goods-management exposes no
restore endpoint anyway — see "nice to have" below).

**Fix** — Implement the decrement as an atomic, guarded UPDATE so it can't oversell and so
order can rely on the result:
```sql
UPDATE litemall_goods_product SET number = number - ? WHERE id = ? AND number >= ?
```
Return a clear failure (non-zero `errno` / 4xx) when no row is affected (insufficient stock),
so order can fail/roll back the placement instead of overselling.

---

## Defect 2 — `/srv/goods/*` read contract is broken in two ways

The order side already routes around both of these in its ACL
(`LitemallGoodsFacadeImpl`): it fetches goods via per-id `GET /srv/goods/goodsdetail`
instead of `/srv/goods/batch`, and it stamps the authoritative `goodsId` itself. Fixing
them here lets order (and any other consumer) use the batch path and trust the payload.

### 2a — `POST /srv/goods/batch` serializes Map keys as object identity (unusable)

`LitemallGoodsController.java` `batchGoods(...)` (~line 306) returns
`Map<LitemallGoodsId, LitemallGoodsAggregate>`. `LitemallGoodsId` has no Jackson key
serializer / value `toString()`, so the map keys serialize to the JVM identity string:

```
POST /srv/goods/batch  [1181000]
→ {"org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallGoodsId@3b488a9a":
     {"goodsId":{"id":1181000}, ...}}
```
The key carries no goodsId, so a consumer can't index the result. **Fix:** key the response
by the plain integer goodsId (`Map<Integer, ...>`), or add a `@JsonKey` / key serializer /
meaningful `toString()` on `LitemallGoodsId`.

### 2b — `GET /srv/goods/product?id={goodsId}` returns a mis-mapped per-row `goodsId`

Each product variant comes back with `goodsId` equal to the product's own sequence
(1, 2, 3…) instead of the real parent goodsId:

```
GET /srv/goods/product?id=1181000
→ [ {"goodsProductId":{"id":"1"}, "goodsId":{"id":1},   "number":100, ...},
    {"goodsProductId":{"id":"2"}, "goodsId":{"id":2},   "number":198, ...},
    {"goodsProductId":{"id":"3"}, "goodsId":{"id":3},   "number":300, ...} ]
```
Every `goodsId` here should be `1181000`. **Fix:** populate each product aggregate's
`goodsId` from the parent goods, not the row index. (`goodsProductId` is correct; note it
is also serialized as a string — harmless but inconsistent with the int ids elsewhere.)

---

## Nice to have (not blocking) — stock-restore endpoint

Order's rollback/cancellation path wants to release a previously-reserved reduce, but
goods-management has no inverse of `stock/reduce`. Today
`LitemallGoodsFacade.restoreStock` is a logged no-op. Once Defect 1 is real, consider adding
a `POST /srv/goods/stock/restore` (or an `increase` flag) so compensation can actually
return stock.

---

## Not a goods-management issue (recorded for completeness)

Order's post-commit domain events (`orderCreated` / `orderPaymentSucceeded`, Spring Cloud
Stream) fail with `MessageDispatchingException` because **Kafka isn't running** in this
environment. The order still commits (HTTP 201); start the broker to exercise events.
