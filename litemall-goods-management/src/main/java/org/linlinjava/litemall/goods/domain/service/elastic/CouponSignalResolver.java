package org.linlinjava.litemall.goods.domain.service.elastic;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.linlinjava.litemall.db.dao.LitemallCouponMapper;
import org.linlinjava.litemall.db.domain.LitemallCoupon;
import org.linlinjava.litemall.db.domain.LitemallCouponExample;
import org.linlinjava.litemall.db.util.CouponConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Wave-19 {@code coupon_flag} basis: a memoized snapshot of the ACTIVE, publicly claimable
 * ({@code TYPE_COMMON}, status NORMAL, inside any fixed time window) coupons, matched
 * against a product's id + ancestor category chain at document-build time. Read-only over
 * the shared {@code litemall_coupon} table — the inverse of promotion's goods reads, same
 * shared-DB precedent. ~60s TTL keeps a full reindex at ONE coupon query per minute
 * instead of one per document; a same-day coupon create lags at most one refresh cycle
 * on incremental upserts (nightly reindex covers the rest — accepted v1 semantics).
 *
 * <p>Exhaustion (claim counts vs {@code total}) is deliberately NOT checked — the flag
 * means "a coupon exists for this product", and the claim path stays authoritative.
 */
@Component
public class CouponSignalResolver {

    private static final Logger log = LoggerFactory.getLogger(CouponSignalResolver.class);
    private static final long TTL_MS = 60 * 1000L;

    private final LitemallCouponMapper couponMapper;

    private volatile List<LitemallCoupon> snapshot = List.of();
    private volatile long builtAt = 0L;

    public CouponSignalResolver(LitemallCouponMapper couponMapper) {
        this.couponMapper = couponMapper;
    }

    /** 1 when any active claimable coupon's scope matches this product, else 0. */
    public int couponFlag(Integer goodsId, List<String> ancestorCategoryIds) {
        if (goodsId == null) {
            return 0;
        }
        for (LitemallCoupon coupon : active()) {
            if (matches(coupon, goodsId, ancestorCategoryIds)) {
                return 1;
            }
        }
        return 0;
    }

    private boolean matches(LitemallCoupon coupon, int goodsId, List<String> ancestorCategoryIds) {
        Short goodsType = coupon.getGoodsType();
        if (goodsType == null || CouponConstant.GOODS_TYPE_ALL.equals(goodsType)) {
            return true;
        }
        Integer[] values = coupon.getGoodsValue();
        if (values == null || values.length == 0) {
            return false;
        }
        if (CouponConstant.GOODS_TYPE_ARRAY.equals(goodsType)) {
            for (Integer value : values) {
                if (value != null && value == goodsId) {
                    return true;
                }
            }
            return false;
        }
        if (CouponConstant.GOODS_TYPE_CATEGORY.equals(goodsType) && ancestorCategoryIds != null) {
            // Wave-18 semantics: the admin's picked ids are ANY level; the product's full
            // ancestor chain is matched, so an L1-scoped coupon flags its whole subtree.
            for (Integer value : values) {
                if (value != null && ancestorCategoryIds.contains(String.valueOf(value))) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<LitemallCoupon> active() {
        long now = System.currentTimeMillis();
        List<LitemallCoupon> snap = snapshot;
        if (now - builtAt < TTL_MS) {
            return snap;
        }
        try {
            LitemallCouponExample example = new LitemallCouponExample();
            example.or()
                    .andTypeEqualTo(CouponConstant.TYPE_COMMON)
                    .andStatusEqualTo(CouponConstant.STATUS_NORMAL)
                    .andDeletedEqualTo(false);
            List<LitemallCoupon> rows = couponMapper.selectByExample(example);
            List<LitemallCoupon> live = new ArrayList<>(rows.size());
            LocalDateTime nowTime = LocalDateTime.now();
            for (LitemallCoupon coupon : rows) {
                if (inWindow(coupon, nowTime)) {
                    live.add(coupon);
                }
            }
            snapshot = live;
            builtAt = now;
            return live;
        } catch (RuntimeException ex) {
            // Fail-soft: indexing must never die on the coupon read — stale (or empty)
            // snapshot simply means the flag lags until the next successful refresh.
            log.warn("coupon_flag snapshot refresh failed (keeping previous): {}", ex.getMessage());
            builtAt = now;
            return snap;
        }
    }

    private static boolean inWindow(LitemallCoupon coupon, LocalDateTime now) {
        if (!CouponConstant.TIME_TYPE_TIME.equals(coupon.getTimeType())) {
            return true; // days-from-claim coupons are claimable while status is NORMAL
        }
        return (coupon.getStartTime() == null || !now.isBefore(coupon.getStartTime()))
                && (coupon.getEndTime() == null || !now.isAfter(coupon.getEndTime()));
    }
}
