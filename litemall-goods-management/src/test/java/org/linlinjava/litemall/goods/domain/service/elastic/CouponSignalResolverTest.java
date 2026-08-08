package org.linlinjava.litemall.goods.domain.service.elastic;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.LitemallCouponMapper;
import org.linlinjava.litemall.db.domain.LitemallCoupon;
import org.linlinjava.litemall.db.util.CouponConstant;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * coupon_flag matrix: ALL scope flags everything; goods scope flags only listed ids;
 * category scope matches ANY ancestor (Wave-18 semantics — an L1 coupon flags its whole
 * subtree); a fixed-window coupon outside its window never flags; a mapper failure
 * degrades to the previous snapshot instead of killing indexing.
 */
public class CouponSignalResolverTest {

    private LitemallCouponMapper couponMapper;
    private CouponSignalResolver resolver;

    @BeforeEach
    public void setUp() {
        couponMapper = mock(LitemallCouponMapper.class);
        resolver = new CouponSignalResolver(couponMapper);
    }

    private static LitemallCoupon coupon(Short goodsType, Integer[] goodsValue) {
        LitemallCoupon coupon = new LitemallCoupon();
        coupon.setGoodsType(goodsType);
        coupon.setGoodsValue(goodsValue);
        coupon.setTimeType(CouponConstant.TIME_TYPE_DAYS);
        return coupon;
    }

    @Test
    public void allScopeFlagsEveryProduct() {
        when(couponMapper.selectByExample(any()))
                .thenReturn(List.of(coupon(CouponConstant.GOODS_TYPE_ALL, null)));
        assertEquals(1, resolver.couponFlag(42, List.of("1", "1008")));
        assertEquals(1, resolver.couponFlag(77, List.of()));
    }

    @Test
    public void goodsScopeFlagsOnlyListedIds() {
        when(couponMapper.selectByExample(any()))
                .thenReturn(List.of(coupon(CouponConstant.GOODS_TYPE_ARRAY, new Integer[]{42, 43})));
        assertEquals(1, resolver.couponFlag(42, List.of("1008")));
        assertEquals(0, resolver.couponFlag(44, List.of("1008")));
    }

    @Test
    public void categoryScopeMatchesAnyAncestor() {
        // L1-scoped coupon (1036012); product's chain is [1036012 (L1), 1036013 (leaf)]
        when(couponMapper.selectByExample(any()))
                .thenReturn(List.of(coupon(CouponConstant.GOODS_TYPE_CATEGORY, new Integer[]{1036012})));
        assertEquals(1, resolver.couponFlag(42, List.of("1036012", "1036013")));
        assertEquals(0, resolver.couponFlag(42, List.of("1040000", "1040001")));
    }

    @Test
    public void fixedWindowOutsideNeverFlags() {
        LitemallCoupon expired = coupon(CouponConstant.GOODS_TYPE_ALL, null);
        expired.setTimeType(CouponConstant.TIME_TYPE_TIME);
        expired.setStartTime(LocalDateTime.now().minusDays(10));
        expired.setEndTime(LocalDateTime.now().minusDays(1));
        when(couponMapper.selectByExample(any())).thenReturn(List.of(expired));
        assertEquals(0, resolver.couponFlag(42, List.of("1008")));
    }

    @Test
    public void queryFiltersToClaimableNormalUndeleted() {
        when(couponMapper.selectByExample(any())).thenReturn(List.of());
        resolver.couponFlag(42, List.of());
        ArgumentCaptor<org.linlinjava.litemall.db.domain.LitemallCouponExample> captor =
                ArgumentCaptor.forClass(org.linlinjava.litemall.db.domain.LitemallCouponExample.class);
        org.mockito.Mockito.verify(couponMapper).selectByExample(captor.capture());
        String criteria = captor.getValue().getOredCriteria().get(0).getAllCriteria().stream()
                .map(c -> c.getCondition())
                .reduce("", (a, b) -> a + " | " + b);
        assertTrue(criteria.contains("`type` ="));
        assertTrue(criteria.contains("`status` ="));
        assertTrue(criteria.contains("deleted ="));
    }

    @Test
    public void mapperFailureDegradesToPreviousSnapshotNotAnException() {
        when(couponMapper.selectByExample(any()))
                .thenThrow(new RuntimeException("db down"));
        assertEquals(0, resolver.couponFlag(42, List.of("1008"))); // empty previous snapshot
    }
}
