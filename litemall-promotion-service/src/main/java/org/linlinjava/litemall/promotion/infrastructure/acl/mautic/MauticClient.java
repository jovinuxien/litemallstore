package org.linlinjava.litemall.promotion.infrastructure.acl.mautic;

import org.linlinjava.litemall.promotion.infrastructure.acl.mautic.dto.MauticContactResponse;
import org.linlinjava.litemall.promotion.infrastructure.acl.mautic.dto.MauticSegmentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * Feign client over the Mautic REST API (Phase 3 automation → delivery). Host
 * from {@code litemall.promotion.mautic.base-url} (profile-overridable; no
 * hardcoded host); Basic auth applied by {@link MauticFeignConfig}. Wrapped by
 * {@link MauticDeliveryAdapter} (ACL) so domain/application code depends only on
 * the {@code CampaignDeliveryPort}, never on this client.
 */
@FeignClient(
        name = "mautic",
        url = "${litemall.promotion.mautic.base-url:http://localhost}",
        configuration = MauticFeignConfig.class)
public interface MauticClient {

    /** Create a segment (Mautic list). Body carries {@code name}, {@code alias}, {@code isPublished}. */
    @PostMapping("/api/segments/new")
    MauticSegmentResponse createSegment(@RequestBody Map<String, Object> body);

    /** Create or update a contact. Body carries the field map (incl. the litemall-user-id field). */
    @PostMapping("/api/contacts/new")
    MauticContactResponse createOrUpdateContact(@RequestBody Map<String, Object> body);

    /** Add a contact to a segment. */
    @PostMapping("/api/segments/{segmentId}/contact/{contactId}/add")
    Map<String, Object> addContactToSegment(@PathVariable("segmentId") Integer segmentId,
                                            @PathVariable("contactId") Integer contactId);
}
