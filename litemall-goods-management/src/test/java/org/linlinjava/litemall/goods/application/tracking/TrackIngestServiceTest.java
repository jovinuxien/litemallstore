package org.linlinjava.litemall.goods.application.tracking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.LitemallUserEventMapper;
import org.linlinjava.litemall.db.domain.LitemallUserEvent;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TrackIngestServiceTest {

    private static final String VISITOR = "3f2b8a10-1111-4c39-9b1a-aaaaaaaaaaaa";
    private static final String SESSION = "3f2b8a10-2222-4c39-9b1a-bbbbbbbbbbbb";

    private LitemallUserEventMapper mapper;
    private TrackIngestService service;

    @BeforeEach
    void setup() {
        mapper = Mockito.mock(LitemallUserEventMapper.class);
        service = new TrackIngestService(mapper, true, 1000, 200);
    }

    private IngestContext anonymousCtx() {
        return new IngestContext(VISITOR, SESSION, null, "FR", "desktop", "en-US");
    }

    private TrackEventDto event(String type) {
        TrackEventDto dto = new TrackEventDto();
        dto.setEventId(UUID.randomUUID().toString());
        dto.setType(type);
        dto.setOccurredAt(System.currentTimeMillis());
        dto.setGoodsId(10000553);
        dto.setCategoryId(1300);
        dto.setSearchQuery("summer dress");
        dto.setPageType("pdp");
        return dto;
    }

    @Test
    void acceptsValidBatchAndDrainsWithContextStamped() {
        TrackIngestService.IngestResult result =
                service.offerBatch(List.of(event("view_item"), event("search")), anonymousCtx());
        assertThat(result.accepted()).isEqualTo(2);
        assertThat(result.dropped()).isZero();

        service.drain();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LitemallUserEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(mapper).batchInsertIgnore(captor.capture());
        List<LitemallUserEvent> rows = captor.getValue();
        assertThat(rows).hasSize(2);
        LitemallUserEvent row = rows.get(0);
        assertThat(row.getVisitorId()).isEqualTo(VISITOR);
        assertThat(row.getSessionId()).isEqualTo(SESSION);
        assertThat(row.getOrigin()).isEqualTo(LitemallUserEvent.ORIGIN_CLIENT);
        assertThat(row.getCountryCode()).isEqualTo("FR");
        assertThat(row.getDeviceType()).isEqualTo("desktop");
        assertThat(row.getReceivedAt()).isNotNull();
        assertThat(row.getOccurredAt()).isNotNull();
        assertThat(row.getUserId()).isNull();
    }

    @Test
    void dropsUnknownAndServerOnlyTypesAndBadIds() {
        TrackEventDto unknown = event("view_item");
        unknown.setType("totally_new_type");
        TrackEventDto serverOnly = event("view_item");
        serverOnly.setType("purchase");
        TrackEventDto badId = event("view_item");
        badId.setEventId("not-a-uuid");

        TrackIngestService.IngestResult result =
                service.offerBatch(List.of(unknown, serverOnly, badId), anonymousCtx());
        assertThat(result.accepted()).isZero();
        assertThat(result.dropped()).isEqualTo(3);
        service.drain();
        verify(mapper, never()).batchInsertIgnore(any());
    }

    @Test
    void dropsEventsMissingTheirRequiredField() {
        TrackEventDto noGoods = event("view_item");
        noGoods.setGoodsId(null);
        TrackEventDto noQuery = event("search");
        noQuery.setSearchQuery("  ");
        TrackEventDto noCategory = event("view_category");
        noCategory.setCategoryId(-4);

        TrackIngestService.IngestResult result =
                service.offerBatch(List.of(noGoods, noQuery, noCategory), anonymousCtx());
        assertThat(result.accepted()).isZero();
        assertThat(result.dropped()).isEqualTo(3);
    }

    @Test
    void clampsInsaneClientClocksToTheReceivedWindow() {
        TrackEventDto ancient = event("page_view");
        ancient.setOccurredAt(1000L);
        service.offerBatch(List.of(ancient), anonymousCtx());
        service.drain();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LitemallUserEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(mapper).batchInsertIgnore(captor.capture());
        LitemallUserEvent row = captor.getValue().get(0);
        // clamped to received_at - 7d, i.e. within the last 8 days, not 1970
        assertThat(row.getOccurredAt()).isAfter(LocalDateTime.now().minusDays(8));
    }

    @Test
    void capsBatchesAtFiftyEvents() {
        List<TrackEventDto> big = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            big.add(event("page_view"));
        }
        TrackIngestService.IngestResult result = service.offerBatch(big, anonymousCtx());
        assertThat(result.accepted()).isEqualTo(50);
        assertThat(result.dropped()).isEqualTo(10);
    }

    @Test
    void stitchesIdentityOnlyWhenUserAndVisitorArePresent() {
        IngestContext loggedIn = new IngestContext(VISITOR, SESSION, 42, null, null, null);
        service.offerBatch(List.of(event("view_item")), loggedIn);
        service.drain();
        verify(mapper).insertIdentityLinkIgnore(Mockito.eq(VISITOR), Mockito.eq(42), any(LocalDateTime.class));

        Mockito.reset(mapper);
        service.offerBatch(List.of(event("view_item")), anonymousCtx());
        service.drain();
        verify(mapper, never()).insertIdentityLinkIgnore(anyString(), anyInt(), any());
    }

    @Test
    void consentRecordsFlowEvenWithoutVisitorId() {
        IngestContext noIdentity = new IngestContext(null, null, null, "DE", "mobile", "de");
        service.offerConsent("denied", "analytics", System.currentTimeMillis(), noIdentity);
        service.drain();
        verify(mapper).insertConsentRecord(Mockito.argThat(r ->
                r.getVisitorId() == null && "denied".equals(r.getChoice()) && "DE".equals(r.getCountryCode())));
    }

    @Test
    void drainSurvivesMapperFailuresAndDropsTheBatch() {
        when(mapper.batchInsertIgnore(any())).thenThrow(new RuntimeException("db down"));
        service.offerBatch(List.of(event("view_item")), anonymousCtx());
        service.drain();   // must not throw
        service.drain();   // queue emptied — no second insert attempt
        verify(mapper, Mockito.times(1)).batchInsertIgnore(any());
    }

    @Test
    void oversizePayloadIsDroppedButEventKept() {
        TrackEventDto dto = event("add_to_cart");
        dto.setPayload(Map.of("big", "x".repeat(TrackIngestService.PAYLOAD_MAX_CHARS + 10)));
        service.offerBatch(List.of(dto), anonymousCtx());
        service.drain();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LitemallUserEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(mapper).batchInsertIgnore(captor.capture());
        assertThat(captor.getValue().get(0).getPayload()).isNull();
    }

    @Test
    void disabledServiceAcceptsNothing() {
        TrackIngestService dark = new TrackIngestService(mapper, false, 1000, 200);
        TrackIngestService.IngestResult result = dark.offerBatch(List.of(event("view_item")), anonymousCtx());
        assertThat(result.accepted()).isZero();
        dark.drain();
        verify(mapper, never()).batchInsertIgnore(any());
    }
}
