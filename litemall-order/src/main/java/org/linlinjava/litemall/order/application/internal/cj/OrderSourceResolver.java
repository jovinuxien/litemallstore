package org.linlinjava.litemall.order.application.internal.cj;

import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.order.application.util.exception.order.LitemallOrderServiceException;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCartAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides an order's fulfillment {@code source} ('local' | 'cj') from its cart lines by
 * reading {@code litemall_goods.source} off the native rows (the same local litemall-db
 * read {@link CjOrderLineResolver} uses — order shares the DB with the goods catalog).
 *
 * <p>An order must be HOMOGENEOUS: CJ lines are fulfilled by CJ createOrder at pay time,
 * local lines by our own ship flow, and one order cannot ship through both. The SPA
 * already splits the cart per source before submitting; a mixed submit is therefore a
 * client error, raised as {@link LitemallOrderServiceException} so the orchestrator's
 * pre-check surfaces it as a clean 422 (never a half-CJ order).
 */
@Service
public class OrderSourceResolver {

    private final LitemallGoodsService goodsService;

    public OrderSourceResolver(LitemallGoodsService goodsService) {
        this.goodsService = goodsService;
    }

    /** Resolve 'local' | 'cj' for the cart lines, throwing on a mixed-source cart. */
    public String resolve(List<LitemallCartAggregate> cartList) {
        Set<Integer> goodsIds = cartList.stream()
                .filter(java.util.Objects::nonNull)
                .map(item -> item.getGoodsId().getId())
                .collect(Collectors.toSet());
        Set<String> sources = goodsIds.stream()
                .map(this::sourceOfGoods)
                .collect(Collectors.toSet());
        if (sources.size() > 1) {
            throw new LitemallOrderServiceException(
                    "Cart mixes CJ-fulfilled and locally-fulfilled items; submit them as separate orders");
        }
        return sources.contains(LitemallOrderAggregate.SOURCE_CJ)
                ? LitemallOrderAggregate.SOURCE_CJ
                : LitemallOrderAggregate.SOURCE_LOCAL;
    }

    private String sourceOfGoods(Integer goodsId) {
        LitemallGoods goods = goodsService.findById(goodsId);
        // Unknown / pre-V23 rows read as local; only an explicit 'cj' row routes to CJ.
        return goods != null && LitemallOrderAggregate.SOURCE_CJ.equals(goods.getSource())
                ? LitemallOrderAggregate.SOURCE_CJ
                : LitemallOrderAggregate.SOURCE_LOCAL;
    }
}
