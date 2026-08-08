package org.linlinjava.litemall.promotion.infrastructure.repositories.impl;

import org.linlinjava.litemall.db.dao.LitemallCouponDeliveryMapper;
import org.linlinjava.litemall.db.domain.LitemallCouponDelivery;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallCouponDeliveryRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Thin adapter over the hand-written {@link LitemallCouponDeliveryMapper}
 * (Wave 22, V58). The mapper owns the SQL semantics (paid-status set,
 * criterion composition); this class only maps the raw performance row into
 * the typed record.
 */
@Repository
public class LitemallCouponDeliveryRepositoryImpl implements LitemallCouponDeliveryRepository {

    private final LitemallCouponDeliveryMapper mapper;

    public LitemallCouponDeliveryRepositoryImpl(LitemallCouponDeliveryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<Integer> selectPaidAudience(LocalDateTime paidSince, Integer minFrequency,
                                            BigDecimal minMonetary, int limit) {
        return mapper.selectPaidAudience(paidSince, minFrequency, minMonetary, limit);
    }

    @Override
    public void add(LitemallCouponDelivery delivery) {
        mapper.insertDelivery(delivery);
    }

    @Override
    public DeliveryPage findDeliveries(Integer couponId, int page, int limit) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        long total = mapper.countByCoupon(couponId);
        List<LitemallCouponDelivery> rows =
                mapper.selectByCoupon(couponId, (safePage - 1) * safeLimit, safeLimit);
        return new DeliveryPage(total, rows);
    }

    @Override
    public CouponPerformance performance(int couponId) {
        Map<String, Object> row = mapper.selectPerformance(couponId);
        if (row == null) {
            return new CouponPerformance(0, 0, 0, BigDecimal.ZERO);
        }
        return new CouponPerformance(
                asLong(row.get("granted")),
                asLong(row.get("used")),
                asLong(row.get("ordersCount")),
                asDecimal(row.get("revenue")));
    }

    private long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    private BigDecimal asDecimal(Object value) {
        if (value instanceof BigDecimal d) {
            return d;
        }
        return value instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : BigDecimal.ZERO;
    }
}
