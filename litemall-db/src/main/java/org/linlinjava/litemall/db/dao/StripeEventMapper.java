package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.LitemallStripeEvent;

/**
 * Hand-written mapper for the Stripe webhook idempotency ledger
 * ({@code litemall_stripe_event}, V43). Mirrors {@link MailOutboxMapper}: co-located
 * with the generated DAOs so the existing {@code @MapperScan} + {@code dao/*.xml}
 * mapper-location pattern picks it up.
 */
public interface StripeEventMapper {

    /**
     * Claim an event id for processing. Returns 1 on a fresh claim and <b>0 when the
     * event was already recorded</b> (INSERT IGNORE against the UNIQUE key) — so the
     * caller treats 0 as "already handled, skip" without a separate SELECT. That matters:
     * a read-then-write check would let two concurrent deliveries of the same event both
     * pass, which is exactly the double-processing the ledger exists to prevent.
     */
    int claim(@Param("eventId") String eventId,
              @Param("eventType") String eventType,
              @Param("now") java.time.LocalDateTime now);

    /** Attach the resolved order to a claimed event (audit trail; never gates processing). */
    int attachOrder(@Param("eventId") String eventId,
                    @Param("orderId") Integer orderId,
                    @Param("now") java.time.LocalDateTime now);

    LitemallStripeEvent selectByEventId(@Param("eventId") String eventId);
}
