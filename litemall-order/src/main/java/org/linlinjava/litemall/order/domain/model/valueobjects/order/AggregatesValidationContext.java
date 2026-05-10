package org.linlinjava.litemall.order.domain.model.valueobjects.order;

import lombok.Getter;
import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsProductAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsProductId;

import java.util.*;


@Getter
public class AggregatesValidationContext {

    private final Map<LitemallGoodsId, LitemallGoodsAggregate> goodsMap;
    private final Map<LitemallGoodsProductId, LitemallGoodsProductAggregate> productsMap;
    private final boolean dataLoaded;


    private AggregatesValidationContext(Map<LitemallGoodsId, LitemallGoodsAggregate> goodsMap,
                                        Map<LitemallGoodsProductId, LitemallGoodsProductAggregate> productsMap) {

        this.goodsMap = goodsMap != null ? Collections.unmodifiableMap(new HashMap<>(goodsMap)) : Collections.emptyMap();
        this.productsMap = productsMap != null ? Collections.unmodifiableMap(new HashMap<>(productsMap)) : Collections.emptyMap();
        assert goodsMap != null;
        assert productsMap!= null;
        this.dataLoaded = !goodsMap.isEmpty() && !productsMap.isEmpty();
    }

    // ============================================
    // Factory METHODS
    // ============================================

    public static AggregatesValidationContext create(Map<LitemallGoodsId, LitemallGoodsAggregate> goodsMap,
                                                     Map<LitemallGoodsProductId, LitemallGoodsProductAggregate> productsMap) {
        return new AggregatesValidationContext(goodsMap, productsMap);
    }

    public static AggregatesValidationContext mapsGoodsAndMapsProductsNotFound(){
        return new AggregatesValidationContext(null, null);
    }

    //====================
    // Validation methods
    //====================
    public boolean isValidForValidation() {
        return dataLoaded;
    }

    public boolean containsGoods(LitemallGoodsId goodsId) {
        return goodsMap.containsKey(goodsId);
    }

    public boolean containsProduct(LitemallGoodsProductId productId) {
        return productsMap.containsKey(productId);
    }
    // Accessors
    public LitemallGoodsAggregate getGoods(LitemallGoodsId goodsId) {
        if (!containsGoods(goodsId)) {
            throw new IllegalArgumentException("Goods not found: " + goodsId);
        }
        return goodsMap.get(goodsId);
    }

    public LitemallGoodsProductAggregate getProduct(LitemallGoodsProductId productId) {
        if (!containsProduct(productId)) {
            throw new IllegalArgumentException("Product not found: " + productId);
        }
        return productsMap.get(productId);
    }




    // ============================================
    // UTILITY METHODS
    // ============================================
    public boolean containsAllGoods(Set<LitemallGoodsId> goodsIds){
        return goodsMap.keySet().containsAll(goodsIds);
    }
    public boolean containsAllProducts(Set<LitemallGoodsProductId> productIds){
        return productsMap.keySet().containsAll(productIds);
    }

    public int getGoodsCount(){
        return goodsMap.size();
    }
    public int getProductCount(){
        return productsMap.size();
    }

    public Set<LitemallGoodsId> getLoadedGoodsIds() {
        return Collections.unmodifiableSet(goodsMap.keySet());
    }

    public Set<LitemallGoodsProductId> getLoadedProductIds() {
        return Collections.unmodifiableSet(productsMap.keySet());
    }


    // =========================================================================
    // VALUE OBJECT CONTRACT
    // =========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AggregatesValidationContext that = (AggregatesValidationContext) o;
        return dataLoaded == that.dataLoaded &&
                Objects.equals(goodsMap, that.goodsMap) &&
                Objects.equals(productsMap, that.productsMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(goodsMap, productsMap, dataLoaded);
    }

    @Override
    public String toString() {
        return "AggregatesValidationContext{" +
                "goodsFound=" + goodsMap +
                ", goodsProductFound=" + productsMap +
                '}';
    }
}
