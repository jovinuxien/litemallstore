package org.linlinjava.litemall.promotion.application.ports;

import java.util.List;

/**
 * Port that delivers a targeting decision to the outbound marketing-automation
 * system (Phase 3 automation → delivery). The default implementation is a Mautic
 * REST ACL ({@code infrastructure/acl/mautic}); callers depend only on this port,
 * never on the external client — so an alternative delivery channel can slot in
 * without reworking the targeting flow.
 *
 * <p>Implementations are best-effort: a delivery failure is logged and swallowed
 * and must never unwind the already-committed campaign evaluation.
 */
public interface CampaignDeliveryPort {

    /**
     * Push the campaign's selected audience to the delivery system and trigger the
     * corresponding campaign/segment.
     *
     * @param campaignId          the evaluated campaign
     * @param audienceUserIds     litemall user ids selected by the targeting engine
     * @param linkedPromotionType the Phase-1 mechanic linked to the campaign (e.g. COUPON)
     * @param linkedPromotionId   id of the linked promotion, if any
     */
    void deliverToAudience(Integer campaignId,
                           List<Integer> audienceUserIds,
                           String linkedPromotionType,
                           Integer linkedPromotionId);
}
