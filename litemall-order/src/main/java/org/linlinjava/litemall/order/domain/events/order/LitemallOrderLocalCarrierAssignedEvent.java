package org.linlinjava.litemall.order.domain.events.order;

import lombok.Getter;
import org.linlinjava.litemall.order.domain.events.AbstractLitemallOrderDomainEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;

/**
 * CJ tracking named the local (last-mile) carrier of a shipped order for the first time.
 * Raised once per order — the timeline hop written in the same transaction is the
 * "already told" marker — so the customer hears who delivers the parcel without having
 * to open the order page. Published inside a transaction so the AFTER_COMMIT mail
 * listener sees it.
 */
@Getter
public class LitemallOrderLocalCarrierAssignedEvent extends AbstractLitemallOrderDomainEvent {

    public static final int SCHEMA_VERSION = 1;

    private final LitemallOrderId orderId;
    private final String carrier;
    /** The local carrier's own tracking number; may be blank. */
    private final String localTrackNumber;
    /** May be blank. */
    private final String carrierPhone;
    /** May be blank. */
    private final String carrierEmail;

    public LitemallOrderLocalCarrierAssignedEvent(LitemallOrderId orderId, String carrier, String localTrackNumber,
                                                  String carrierPhone, String carrierEmail) {
        super(SCHEMA_VERSION);
        this.orderId = orderId;
        this.carrier = carrier;
        this.localTrackNumber = localTrackNumber;
        this.carrierPhone = carrierPhone;
        this.carrierEmail = carrierEmail;
    }
}
