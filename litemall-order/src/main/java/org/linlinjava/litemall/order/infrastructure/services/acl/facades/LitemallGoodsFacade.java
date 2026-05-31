package org.linlinjava.litemall.order.infrastructure.services.acl.facades;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsProductAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsProductId;

import java.util.Map;
import java.util.Set;

/**
 * Anti-corruption layer over goods-management. This is the ONLY seam through
 * which order code (domain + application) reads goods/product data and reserves
 * stock for the placement path; the Feign client must never be referenced from
 * domain or application code directly (see docs/adr-goods-acquisition.md).
 *
 * <p>Every method gives an immediate, authoritative answer (synchronous Feign,
 * timeouts + circuit breaker behind it). On a goods-management outage or error
 * response the implementation throws
 * {@link org.linlinjava.litemall.order.application.util.exception.product.LitemallGoodsServiceUnavailableException}
 * so the placement transaction rolls back cleanly — no order, no stock taken.
 */
public interface LitemallGoodsFacade {

    /** Fetch a single product variant (by product id) for stock/price validation. */
    LitemallGoodsProductAggregate getGoodsProduct(LitemallGoodsProductId productId);

    /** Batch-fetch goods aggregates keyed by goods id. Empty input → empty map. */
    Map<LitemallGoodsId, LitemallGoodsAggregate> batchGetGoods(Set<Integer> goodsIds);

    /** Batch-fetch product variants keyed by product id. Empty input → empty map. */
    Map<LitemallGoodsProductId, LitemallGoodsProductAggregate> batchGetProducts(Set<Integer> productIds);

    /**
     * Reserve/reduce stock for the given product ids → requested quantities.
     * Returns a per-product success map. Empty input → empty map.
     */
    Map<Integer, Boolean> reduceStock(Map<Integer, Integer> productQuantities);
}
