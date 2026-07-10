package org.linlinjava.litemall.order.infrastructure.services.acl.facades;

import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsProductAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Anti-corruption layer over goods-management. This is the ONLY seam through which
 * order code (domain + application) reads goods/product data and reserves stock for
 * the placement path; the Feign client must never be referenced from domain or
 * application code directly (see docs/adr-goods-acquisition.md).
 *
 * <p>goods-management's real contract is goodsId-centric ({@code GET /srv/goods/product?id=goodsId}
 * returns all variants of a goods) and offers only a single, non-batch
 * {@code POST /srv/goods/stock/reduce}; its {@code /srv/goods/batch} is unusable
 * (Map keys serialize to object identity). This facade absorbs that impedance:
 * goods are fetched one detail call per id, products are fetched per goods, and a
 * batch reduce is issued as a loop of single reduces. On any goods-management
 * outage or error it throws
 * {@link org.linlinjava.litemall.order.application.util.exception.product.LitemallGoodsServiceUnavailableException}
 * so the placement transaction rolls back cleanly — no order, no stock taken.
 */
public interface LitemallGoodsFacade {

    /** Fetch goods aggregates by id (one detail call per id). Empty input → empty map. */
    Map<LitemallGoodsId, LitemallGoodsAggregate> batchGetGoods(Set<Integer> goodsIds);

    /**
     * Fetch all product variants of a goods. Each returned product's goodsId is set
     * to {@code goodsId} (authoritative) rather than goods-management's per-row value,
     * which is mis-mapped upstream.
     */
    List<LitemallGoodsProductAggregate> getProductsByGoods(LitemallGoodsId goodsId);

    /**
     * Reserve/reduce stock for the given product ids → requested quantities, issued
     * as one {@code /srv/goods/stock/reduce} call per product. Returns a per-product
     * success map. Empty input → empty map.
     *
     * <p><strong>NOT atomic across products:</strong> with no remote batch reduce, a
     * mid-loop failure leaves earlier products decremented (and {@link #restoreStock}
     * cannot undo them) — the caller must treat any {@code false}/missing entry as a
     * failed reservation, abort the placement, and log the confirmed entries for
     * reconciliation (see docs/adr-goods-acquisition.md, "Actual reserve guarantee").
     */
    Map<Integer, Boolean> reduceStock(Map<Integer, Integer> productQuantities);

    /**
     * Compensating release of previously-reserved stock (inverse of
     * {@link #reduceStock}), one {@code /srv/goods/stock/restore} call per product
     * (errno 632 = unknown/deleted product). Called from rollback / order-cancellation
     * paths, so it is <strong>best-effort and MUST NOT throw</strong>: a failed restore
     * logs and reports {@code false} for that product (at-most-once — the goods side is
     * not idempotent), never breaking the rollback. Empty input → empty map.
     */
    Map<Integer, Boolean> restoreStock(Map<Integer, Integer> productQuantities);
}
