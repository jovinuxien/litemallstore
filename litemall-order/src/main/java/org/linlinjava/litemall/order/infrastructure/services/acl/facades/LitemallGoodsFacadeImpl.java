package org.linlinjava.litemall.order.infrastructure.services.acl.facades;

import com.fasterxml.jackson.databind.JsonNode;
import org.linlinjava.litemall.order.application.util.exception.product.LitemallGoodsServiceUnavailableException;
import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsProductAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsProductId;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.GoodsServiceFeignClient;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.utils.ReduceStockRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Sole adapter between order code and goods-management. Maps goods-management's raw
 * {@code /srv/goods/*} JSON onto order's goods aggregates (taking only the fields the
 * placement path needs, to dodge cross-service DTO/format mismatches) and converts
 * any transport/error outcome into {@link LitemallGoodsServiceUnavailableException}.
 */
@Component
public class LitemallGoodsFacadeImpl implements LitemallGoodsFacade {

    private static final Logger log = LoggerFactory.getLogger(LitemallGoodsFacadeImpl.class);

    private final GoodsServiceFeignClient goodsServiceFeignClient;

    public LitemallGoodsFacadeImpl(GoodsServiceFeignClient goodsServiceFeignClient) {
        this.goodsServiceFeignClient = goodsServiceFeignClient;
    }

    @Override
    public Map<LitemallGoodsId, LitemallGoodsAggregate> batchGetGoods(Set<Integer> goodsIds) {
        if (goodsIds == null || goodsIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<LitemallGoodsId, LitemallGoodsAggregate> result = new HashMap<>();
        for (Integer goodsId : goodsIds) {
            JsonNode node = callRaw(() -> goodsServiceFeignClient.getGoodsDetail(goodsId),
                    "Get goods detail " + goodsId);
            if (node == null || node.isNull() || node.path("goodsId").isMissingNode()) {
                throw new LitemallGoodsServiceUnavailableException(
                        "Goods " + goodsId + " not found in goods-management", null);
            }
            result.put(new LitemallGoodsId(goodsId), mapGoods(node));
        }
        return result;
    }

    @Override
    public List<LitemallGoodsProductAggregate> getProductsByGoods(LitemallGoodsId goodsId) {
        JsonNode arr = callRaw(() -> goodsServiceFeignClient.getProductsByGoods(goodsId.getId()),
                "Get products for goods " + goodsId.getId());
        List<LitemallGoodsProductAggregate> products = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (JsonNode p : arr) {
                products.add(mapProduct(p, goodsId));
            }
        }
        return products;
    }

    @Override
    public Map<Integer, Boolean> reduceStock(Map<Integer, Integer> productQuantities) {
        if (productQuantities == null || productQuantities.isEmpty()) {
            return Collections.emptyMap();
        }
        // goods-management has no batch reduce; issue one reduce per product. A non-zero
        // errno (or transport error) marks that product as not reduced — the caller
        // treats any unconfirmed product as a failed reservation and rolls back.
        Map<Integer, Boolean> results = new HashMap<>();
        for (Map.Entry<Integer, Integer> e : productQuantities.entrySet()) {
            Integer productId = e.getKey();
            try {
                var resp = goodsServiceFeignClient.reduceStock(new ReduceStockRequest(productId, e.getValue()));
                results.put(productId, resp != null && resp.getErrno() == 0);
            } catch (RuntimeException ex) {
                log.error("Goods ACL 'reduce stock' failed for product {}", productId, ex);
                results.put(productId, false);
            }
        }
        return results;
    }

    @Override
    public Map<Integer, Boolean> restoreStock(Map<Integer, Integer> productQuantities) {
        // Best-effort compensation from rollback/cancel paths: goods-management exposes
        // no stock-restore endpoint, so this is a logged no-op and never throws.
        if (productQuantities != null && !productQuantities.isEmpty()) {
            log.warn("Stock restore requested for {} but goods-management has no restore "
                    + "endpoint; stock NOT released (compensation skipped).", productQuantities);
        }
        return Collections.emptyMap();
    }

    // ---- JSON → aggregate mapping (only the fields placement needs) ----------

    private LitemallGoodsAggregate mapGoods(JsonNode node) {
        LitemallGoodsAggregate goods = new LitemallGoodsAggregate();
        goods.setGoodsId(new LitemallGoodsId(node.path("goodsId").path("id").asInt()));
        goods.setGoodsName(node.path("goodsName").asText(null));
        // goodsSn + picUrl are needed to enrich a cart line built from just
        // {goodsId, productId, number} (legacy /srv/cart/add); placement ignores them.
        goods.setGoodsSn(node.path("goodsSn").asText(null));
        goods.setPicUrl(node.path("picUrl").asText(null));
        return goods;
    }

    private LitemallGoodsProductAggregate mapProduct(JsonNode node, LitemallGoodsId goodsId) {
        LitemallGoodsProductAggregate product = new LitemallGoodsProductAggregate();
        product.setGoodsProductId(new LitemallGoodsProductId(node.path("goodsProductId").path("id").asInt()));
        // Authoritative goodsId from the query — goods-management's per-row goodsId is mis-mapped.
        product.setGoodsId(goodsId);
        product.setNumber(node.path("number").asInt());
        JsonNode amount = node.path("price").path("amount");
        if (!amount.isMissingNode() && !amount.isNull()) {
            product.setPrice(new LitemallMoney(amount.decimalValue()));
        }
        // Variant specifications + image, used to enrich a cart line (legacy /srv/cart/add).
        JsonNode specs = node.path("specifications");
        if (specs.isArray()) {
            List<String> specList = new ArrayList<>();
            specs.forEach(s -> specList.add(s.asText()));
            product.setSpecification(specList.toArray(new String[0]));
        }
        product.setUrl(node.path("url").asText(null));
        return product;
    }

    /**
     * Invoke a raw (un-enveloped) goods-management call and translate any failure
     * (transport error, circuit-open, 4xx/5xx) into {@link LitemallGoodsServiceUnavailableException}
     * so the placement transaction rolls back cleanly.
     */
    private <T> T callRaw(Supplier<T> remote, String operation) {
        try {
            return remote.get();
        } catch (RuntimeException e) {
            log.error("Goods ACL '{}' failed", operation, e);
            throw new LitemallGoodsServiceUnavailableException(operation, e);
        }
    }
}
