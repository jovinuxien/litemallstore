package org.linlinjava.litemall.order.application.internal.cj;

import org.linlinjava.litemall.order.domain.events.LitemallDomainEventPublisher;
import org.linlinjava.litemall.order.domain.events.order.LitemallOrderLocalCarrierAssignedEvent;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallOrderAggregate;
import org.linlinjava.litemall.order.domain.model.repositories.LitemallOrderStatusHistoryRepository;
import org.linlinjava.litemall.order.domain.model.valueobjects.enums.LitemallOrderStatus;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderStatusChange;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjTrackingSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * Tells the customer ONCE which local carrier delivers a CJ parcel (2026-11-22). CJ does
 * not know the last-mile carrier when the order ships, so the shipped mail cannot name it;
 * it appears in CJ tracking later. Until now only the order page showed it, and only to a
 * customer who went looking.
 *
 * <p>State lives on the order timeline, no schema: the {@link #CHANGE_TYPE} hop is the
 * "already told" marker. The hop and the event commit together, so the AFTER_COMMIT mail
 * listener fires exactly when the marker exists.
 */
@Service
public class LocalCarrierNoticeService {

    private static final Logger log = LoggerFactory.getLogger(LocalCarrierNoticeService.class);

    public static final String CHANGE_TYPE = "local_carrier";

    private final LitemallOrderStatusHistoryRepository statusHistoryRepository;
    private final LitemallDomainEventPublisher domainEventPublisher;

    public LocalCarrierNoticeService(LitemallOrderStatusHistoryRepository statusHistoryRepository,
                                     LitemallDomainEventPublisher domainEventPublisher) {
        this.statusHistoryRepository = statusHistoryRepository;
        this.domainEventPublisher = domainEventPublisher;
    }

    /** Cheap local read, asked BEFORE spending a CJ call on the order. */
    @Transactional(readOnly = true)
    public boolean alreadyTold(LitemallOrderAggregate order) {
        return statusHistoryRepository.findByOrderId(order.getOrderId()).stream()
                .anyMatch(hop -> CHANGE_TYPE.equals(hop.getChangeType()));
    }

    /**
     * Records the hop and raises the event when the snapshot names a local carrier and the
     * order was not told before. Returns whether a notice was raised.
     */
    @Transactional
    public boolean tellIfAssigned(LitemallOrderAggregate order, CjTrackingSnapshot tracking) {
        if (tracking == null || !StringUtils.hasText(tracking.getLastMileCarrier())) {
            return false;
        }
        if (alreadyTold(order)) {
            return false;
        }
        String carrier = tracking.getLastMileCarrier().trim();
        String localNumber = trim(tracking.getLastTrackNumber());
        LitemallOrderStatus status = order.getOrderStatus();
        String message = "Local carrier assigned: " + carrier
                + (localNumber.isEmpty() ? "" : " — local tracking number " + localNumber);
        statusHistoryRepository.record(new LitemallOrderStatusChange(
                order.getOrderId(), status, status, CHANGE_TYPE, message, "system", LocalDateTime.now()));
        domainEventPublisher.publish(new LitemallOrderLocalCarrierAssignedEvent(order.getOrderId(), carrier,
                localNumber, trim(tracking.getLastMileCarrierPhone()), trim(tracking.getLastMileCarrierEmail())));
        log.info("Order {} (sn {}): local carrier {} assigned — customer notified",
                order.getOrderId().getId(), order.getOrderSn(), carrier);
        return true;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
