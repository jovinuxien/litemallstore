package org.linlinjava.litemall.promotion.infrastructure.acl.mautic;

import org.linlinjava.litemall.promotion.application.ports.CampaignDeliveryPort;
import org.linlinjava.litemall.promotion.infrastructure.acl.mautic.dto.MauticContactResponse;
import org.linlinjava.litemall.promotion.infrastructure.acl.mautic.dto.MauticSegmentResponse;
import org.linlinjava.litemall.promotion.infrastructure.configuration.MauticProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Anti-corruption layer over {@link MauticClient}: maps a targeting decision onto
 * the Mautic REST contract — ensure a per-campaign segment, upsert each audience
 * member as a contact keyed by the litemall user id, and add them to the segment
 * (which a Mautic campaign listens to). The {@link CampaignDeliveryPort}
 * implementation; the Mautic client never escapes this package.
 *
 * <p>Best-effort and idempotent-ish: disabled or any transport failure is logged
 * and swallowed so a delivery outage never unwinds the committed campaign
 * evaluation. The exact Mautic contract is documented in
 * {@code docs/phase3-marketing-stack-integration.md}.
 */
@Component
public class MauticDeliveryAdapter implements CampaignDeliveryPort {

    private static final Logger logger = LoggerFactory.getLogger(MauticDeliveryAdapter.class);

    private final MauticClient mauticClient;
    private final MauticProperties properties;

    public MauticDeliveryAdapter(MauticClient mauticClient, MauticProperties properties) {
        this.mauticClient = mauticClient;
        this.properties = properties;
    }

    @Override
    public void deliverToAudience(Integer campaignId,
                                  List<Integer> audienceUserIds,
                                  String linkedPromotionType,
                                  Integer linkedPromotionId) {
        if (!properties.isEnabled()) {
            logger.debug("Mautic delivery disabled; skipping campaign {}", campaignId);
            return;
        }
        if (campaignId == null || audienceUserIds == null || audienceUserIds.isEmpty()) {
            logger.info("Mautic delivery: nothing to deliver (campaign={}, audience empty)", campaignId);
            return;
        }
        try {
            Integer segmentId = ensureCampaignSegment(campaignId, linkedPromotionType, linkedPromotionId);
            if (segmentId == null) {
                logger.warn("Mautic delivery: no segment id for campaign {}; aborting push", campaignId);
                return;
            }
            int delivered = 0;
            for (Integer userId : audienceUserIds) {
                if (pushContactToSegment(userId, segmentId, campaignId)) {
                    delivered++;
                }
            }
            logger.info("Mautic delivery: campaign {} → segment {}, {}/{} contacts delivered",
                    campaignId, segmentId, delivered, audienceUserIds.size());
        } catch (Exception e) {
            logger.error("Mautic delivery failed for campaign {}: {}", campaignId, e.getMessage());
        }
    }

    private Integer ensureCampaignSegment(Integer campaignId, String linkedPromotionType, Integer linkedPromotionId) {
        Map<String, Object> body = new HashMap<>();
        String alias = properties.getSegmentAliasPrefix() + campaignId;
        body.put("name", "Litemall campaign " + campaignId
                + (linkedPromotionType != null ? " (" + linkedPromotionType
                        + (linkedPromotionId != null ? " #" + linkedPromotionId : "") + ")" : ""));
        body.put("alias", alias);
        body.put("isPublished", true);
        MauticSegmentResponse response = mauticClient.createSegment(body);
        if (response != null && response.getList() != null) {
            return response.getList().getId();
        }
        return null;
    }

    private boolean pushContactToSegment(Integer userId, Integer segmentId, Integer campaignId) {
        if (userId == null || userId <= 0) {
            return false;
        }
        try {
            Map<String, Object> fields = new HashMap<>();
            fields.put(properties.getUserIdFieldAlias(), userId);
            fields.put("tags", "litemall-campaign-" + campaignId);
            MauticContactResponse contact = mauticClient.createOrUpdateContact(fields);
            if (contact == null || contact.getContact() == null || contact.getContact().getId() == null) {
                logger.warn("Mautic: no contact id for litemall user {} (campaign {})", userId, campaignId);
                return false;
            }
            mauticClient.addContactToSegment(segmentId, contact.getContact().getId());
            return true;
        } catch (Exception e) {
            logger.warn("Mautic: failed to deliver litemall user {} to segment {}: {}",
                    userId, segmentId, e.getMessage());
            return false;
        }
    }
}
