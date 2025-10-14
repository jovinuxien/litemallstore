package org.linlinjava.litemall.order.domain.model.valueobjects.coupon;

import lombok.Getter;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCouponAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCouponUserAggregate;
import org.linlinjava.litemall.order.domain.model.agregates.goods.LitemallGoodsAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class CouponValidationContext {

    private final LitemallCouponAggregate coupon;
    private final LitemallCouponUserAggregate couponUser;
    @Getter
    private final Map<LitemallGoodsId, LitemallGoodsAggregate> goodsMap;
    @Getter
    private final boolean couponFound;
    @Getter
    private final boolean couponUserFound;



    private CouponValidationContext(LitemallCouponAggregate coupon, LitemallCouponUserAggregate couponUser,
                                    Map<LitemallGoodsId, LitemallGoodsAggregate> mapGoods){
        this.coupon = coupon;
        this.couponUser = couponUser;
        this.goodsMap = mapGoods != null ? Collections.unmodifiableMap(new HashMap<>(mapGoods)) : Collections.emptyMap();// Defensive copy
        this.couponFound = coupon!= null;
        this.couponUserFound = couponUser!= null;
    }

    // ============================================
    // Factory METHODS
    // ============================================

    public static CouponValidationContext create(LitemallCouponAggregate coupon, LitemallCouponUserAggregate couponUser,
                                                 Map<LitemallGoodsId, LitemallGoodsAggregate> mapGoods){
        return new CouponValidationContext(coupon, couponUser, mapGoods);
    }

    public static CouponValidationContext couponNotFound(LitemallCouponAggregate coupon){
        return new CouponValidationContext(coupon, null, null);
    }

    public static CouponValidationContext userCouponNotFound(LitemallCouponUserAggregate couponUser){
        return new CouponValidationContext(null, couponUser, null);
    }
    public static CouponValidationContext couponAndCouponNotFound(){
        return new CouponValidationContext(null, null, null);
    }

    public static CouponValidationContext mapGoodsNotFound(){
        return new CouponValidationContext(null, null, null);
    }

    // ============================================
    //  VALIDATION METHODS
    // ============================================

    public boolean isValidForValidation(){
        return couponFound && couponUserFound && !goodsMap.isEmpty();
    }

    public boolean hasGoodsData(){
        return goodsMap != null && !goodsMap.isEmpty();
    }

    public boolean containsGoods(LitemallGoodsId goodsId){
        return goodsMap.containsKey(goodsId);
    }

    public boolean isValidForDiscountCalculation(){
        return couponFound && hasGoodsData();
    }
    // ============================================
    //  ACCESSORIES AND SAFELY CHECKS
    // ============================================
    public LitemallCouponAggregate getCoupon() {
        if (!couponFound) {
            throw new IllegalStateException("Coupon not available in validation context");
        }
        return coupon;
    }

    public LitemallCouponUserAggregate getCouponUser() {
        if (!couponUserFound) {
            throw new IllegalStateException("Coupon user not available in validation context");
        }
        return couponUser;
    }

    public LitemallGoodsAggregate getGoods(LitemallGoodsId goodsId) {
        if (!containsGoods(goodsId)) {
            throw new IllegalArgumentException("Goods not found in validation context: " + goodsId);
        }
        return goodsMap.get(goodsId);
    }

    public LitemallGoodsAggregate getGoodsOrDefault(LitemallGoodsId goodsId, LitemallGoodsAggregate defaultValue) {
        return goodsMap.getOrDefault(goodsId, defaultValue);
    }

    public boolean hasGoods(LitemallGoodsId goodsId) {
        return goodsMap.containsKey(goodsId);
    }


    // =========================================================================
    // UTILITY METHODS
    // =========================================================================
    public CouponValidationContext withCoupon(LitemallCouponAggregate newCoupon) {
        return new CouponValidationContext(newCoupon, this.couponUser, this.goodsMap);
    }


    public CouponValidationContext withCouponUser(LitemallCouponUserAggregate newCouponUser) {
        return new CouponValidationContext(this.coupon, newCouponUser, this.goodsMap);
    }


    public CouponValidationContext withGoodsMap(Map<LitemallGoodsId, LitemallGoodsAggregate> newGoodsMap) {
        return new CouponValidationContext(this.coupon, this.couponUser, newGoodsMap);
    }

    public int getGoodsCount() {
        return goodsMap.size();
    }


// =========================================================================
// VALUE OBJECT CONTRACT
// =========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CouponValidationContext that = (CouponValidationContext) o;
        return couponFound == that.couponFound &&
                couponUserFound == that.couponUserFound &&
                Objects.equals(coupon, that.coupon) &&
                Objects.equals(couponUser, that.couponUser) &&
                Objects.equals(goodsMap, that.goodsMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(coupon, couponUser, goodsMap, couponFound, couponUserFound);
    }

    @Override
    public String toString() {
        return "CouponValidationContext{" +
                "couponFound=" + couponFound +
                ", couponUserFound=" + couponUserFound +
                ", goodsCount=" + getGoodsCount() +
                '}';
    }






    // =========================================================================
    // BUILDER PATTERN (Optionnel - pour une construction plus fluide)
    // =========================================================================

    public static class Builder {
        private LitemallCouponAggregate coupon;
        private LitemallCouponUserAggregate couponUser;
        private Map<LitemallGoodsId, LitemallGoodsAggregate> goodsMap = new HashMap<>();

        public Builder coupon(LitemallCouponAggregate coupon) {
            this.coupon = coupon;
            return this;
        }

        public Builder couponUser(LitemallCouponUserAggregate couponUser) {
            this.couponUser = couponUser;
            return this;
        }

        public Builder goodsMap(Map<LitemallGoodsId, LitemallGoodsAggregate> goodsMap) {
            this.goodsMap = new HashMap<>(goodsMap);
            return this;
        }

        public Builder addGoods(LitemallGoodsId goodsId, LitemallGoodsAggregate goods) {
            this.goodsMap.put(goodsId, goods);
            return this;
        }

        public CouponValidationContext build() {
            return new CouponValidationContext(coupon, couponUser, goodsMap);
        }
    }

    public static Builder builder() {
        return new Builder();
    }


}
