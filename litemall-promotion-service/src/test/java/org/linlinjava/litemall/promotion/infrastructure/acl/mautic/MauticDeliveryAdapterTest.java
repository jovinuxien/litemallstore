package org.linlinjava.litemall.promotion.infrastructure.acl.mautic;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.promotion.application.ports.CustomerContactProvider;
import org.linlinjava.litemall.promotion.infrastructure.acl.mautic.dto.MauticContactResponse;
import org.linlinjava.litemall.promotion.infrastructure.acl.mautic.dto.MauticSegmentResponse;
import org.linlinjava.litemall.promotion.infrastructure.configuration.MauticProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for the Mautic ACL — maps a targeting decision onto ensure-segment +
 * upsert-contact + add-to-segment, enriches contacts with email + nickname and
 * skips email-less users (Wave 6), is a no-op when disabled/empty, and swallows
 * transport failures. Uses recording fakes (no Mockito).
 */
class MauticDeliveryAdapterTest {

    /** In-memory contact directory: user 42/43 have emails, 44 has none. */
    private static class FakeContactProvider implements CustomerContactProvider {
        final Map<Integer, CustomerContact> contacts = new HashMap<>();

        FakeContactProvider() {
            contacts.put(42, new CustomerContact(42, "user42@example.com", "Fortytwo"));
            contacts.put(43, new CustomerContact(43, "user43@example.com", null));
            contacts.put(44, new CustomerContact(44, null, "Nomail"));
        }

        @Override
        public Optional<CustomerContact> contactOf(Integer userId) {
            return Optional.ofNullable(contacts.get(userId));
        }
    }

    /** Recording fake that captures calls and replays scripted responses. */
    private static class RecordingMauticClient implements MauticClient {
        int segmentCreateCount = 0;
        final List<Map<String, Object>> contactBodies = new ArrayList<>();
        final List<int[]> segmentAdds = new ArrayList<>();   // [segmentId, contactId]
        boolean failSegmentCreation = false;
        int segmentId = 9;
        int nextContactId = 100;

        @Override
        public MauticSegmentResponse createSegment(Map<String, Object> body) {
            segmentCreateCount++;
            if (failSegmentCreation) {
                throw new RuntimeException("mautic down");
            }
            MauticSegmentResponse r = new MauticSegmentResponse();
            MauticSegmentResponse.Segment s = new MauticSegmentResponse.Segment();
            s.setId(segmentId);
            r.setList(s);
            return r;
        }

        @Override
        public MauticContactResponse createOrUpdateContact(Map<String, Object> body) {
            contactBodies.add(body);
            MauticContactResponse r = new MauticContactResponse();
            MauticContactResponse.Contact c = new MauticContactResponse.Contact();
            c.setId(++nextContactId);
            r.setContact(c);
            return r;
        }

        @Override
        public Map<String, Object> addContactToSegment(Integer segmentId, Integer contactId) {
            segmentAdds.add(new int[]{segmentId, contactId});
            return Map.of("success", 1);
        }
    }

    private MauticProperties enabledProps() {
        MauticProperties p = new MauticProperties();
        p.setEnabled(true);
        return p;
    }

    @Test
    void deliversAudienceToCampaignSegment() {
        RecordingMauticClient client = new RecordingMauticClient();
        MauticDeliveryAdapter adapter = new MauticDeliveryAdapter(client, enabledProps(), new FakeContactProvider());

        adapter.deliverToAudience(5, List.of(42, 43), "COUPON", 7);

        assertEquals(1, client.segmentCreateCount, "one segment ensured");
        assertEquals(2, client.contactBodies.size(), "one contact upsert per audience member");
        assertEquals(2, client.segmentAdds.size(), "each contact added to the segment");
        assertEquals(9, client.segmentAdds.get(0)[0]);
        assertEquals(101, client.segmentAdds.get(0)[1]);
        assertEquals(102, client.segmentAdds.get(1)[1]);
        // The litemall user id is carried on the contact via the configured field.
        assertEquals(42, client.contactBodies.get(0).get("litemall_user_id"));
    }

    @Test
    void contactsCarryEmailAndNickname() {
        RecordingMauticClient client = new RecordingMauticClient();
        MauticDeliveryAdapter adapter = new MauticDeliveryAdapter(client, enabledProps(), new FakeContactProvider());

        adapter.deliverToAudience(5, List.of(42, 43), "COUPON", 7);

        assertEquals("user42@example.com", client.contactBodies.get(0).get("email"));
        assertEquals("Fortytwo", client.contactBodies.get(0).get("firstname"));
        assertEquals("user43@example.com", client.contactBodies.get(1).get("email"));
        assertNull(client.contactBodies.get(1).get("firstname"), "no nickname → field omitted");
    }

    @Test
    void skipsUsersWithoutEmailWithoutFailingTheBatch() {
        RecordingMauticClient client = new RecordingMauticClient();
        MauticDeliveryAdapter adapter = new MauticDeliveryAdapter(client, enabledProps(), new FakeContactProvider());

        // 44 has no email, 99 is unknown — both skipped; 42 still delivers.
        adapter.deliverToAudience(5, List.of(44, 99, 42), "COUPON", 7);

        assertEquals(1, client.contactBodies.size(), "only the email-bearing user is upserted");
        assertEquals(42, client.contactBodies.get(0).get("litemall_user_id"));
        assertEquals(1, client.segmentAdds.size());
    }

    @Test
    void noOpWhenDisabled() {
        RecordingMauticClient client = new RecordingMauticClient();
        MauticDeliveryAdapter adapter = new MauticDeliveryAdapter(client, new MauticProperties(), new FakeContactProvider());
        adapter.deliverToAudience(5, List.of(42), "COUPON", 7);
        assertEquals(0, client.segmentCreateCount);
        assertTrue(client.segmentAdds.isEmpty());
    }

    @Test
    void noOpWhenAudienceEmpty() {
        RecordingMauticClient client = new RecordingMauticClient();
        MauticDeliveryAdapter adapter = new MauticDeliveryAdapter(client, enabledProps(), new FakeContactProvider());
        adapter.deliverToAudience(5, List.of(), "COUPON", 7);
        assertEquals(0, client.segmentCreateCount);
        assertTrue(client.segmentAdds.isEmpty());
    }

    @Test
    void swallowsSegmentCreationFailure() {
        RecordingMauticClient client = new RecordingMauticClient();
        client.failSegmentCreation = true;
        MauticDeliveryAdapter adapter = new MauticDeliveryAdapter(client, enabledProps(), new FakeContactProvider());
        // Must not throw through the caller (committed evaluation).
        adapter.deliverToAudience(5, List.of(42), "COUPON", 7);
        assertTrue(client.segmentAdds.isEmpty(), "no contacts added when segment creation fails");
    }
}
