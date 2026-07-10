package org.linlinjava.litemall.promotion.domain.service;

import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallCouponAggregate;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallCouponTimeType;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Pure coupon business rules with no persistence. Today: derive the concrete
 * validity window stamped onto a held coupon at receive time, honouring the
 * definition's {@link LitemallCouponTimeType} (relative days vs absolute
 * window).
 */
@Service
public class LitemallCouponDomainService {

    /**
     * Compute the [start, end] a freshly-received coupon should carry.
     * <ul>
     *   <li>DAYS: window starts now and runs for {@code days} days
     *       (a null/zero {@code days} yields an open-ended window).</li>
     *   <li>TIME: inherits the definition's absolute window.</li>
     * </ul>
     */
    public ValidityWindow computeValidityWindow(LitemallCouponAggregate coupon, LocalDateTime receiveTime) {
        if (LitemallCouponTimeType.TIME.equals(coupon.getTimeType())) {
            return new ValidityWindow(coupon.getStartTime(), coupon.getEndTime());
        }
        LocalDateTime end = null;
        if (coupon.getDays() != null && coupon.getDays() > 0) {
            end = receiveTime.plusDays(coupon.getDays());
        }
        return new ValidityWindow(receiveTime, end);
    }

    /** Immutable [start, end] holder for a coupon validity window. */
    public static final class ValidityWindow {
        private final LocalDateTime start;
        private final LocalDateTime end;

        public ValidityWindow(LocalDateTime start, LocalDateTime end) {
            this.start = start;
            this.end = end;
        }

        public LocalDateTime getStart() { return start; }
        public LocalDateTime getEnd() { return end; }
    }
}
