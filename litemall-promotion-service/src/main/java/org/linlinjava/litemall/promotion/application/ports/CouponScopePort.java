package org.linlinjava.litemall.promotion.application.ports;

import java.util.List;

/**
 * Wave-18 coupon scope facts resolver. Coupons store the admin's picked
 * category ids AS-IS (any tree level, L1 encouraged) while carts carry LEAF
 * category ids — this port turns a cart's goods/category ids into the full
 * ancestor-expanded category set so {@code matchesGoods} (which matches ids
 * literally) works for L1-scoped coupons, and future CJ-sync leaves are
 * covered automatically.
 */
public interface CouponScopePort {

    /**
     * The cart's category ids expanded up the {@code litemall_category}
     * ancestor chain. When {@code categoryIds} is null/empty they are first
     * derived from {@code goodsIds} (one batched read). Returns an empty list
     * when nothing can be resolved — never null.
     */
    List<Integer> expandCategoryIds(List<Integer> goodsIds, List<Integer> categoryIds);
}
