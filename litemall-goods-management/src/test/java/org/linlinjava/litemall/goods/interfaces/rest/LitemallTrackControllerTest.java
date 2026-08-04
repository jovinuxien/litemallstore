package org.linlinjava.litemall.goods.interfaces.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.goods.application.tracking.IngestContext;
import org.linlinjava.litemall.goods.application.tracking.TrackEventDto;
import org.linlinjava.litemall.goods.application.tracking.TrackIngestService;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LitemallTrackControllerTest {

    private static final String VISITOR = "3f2b8a10-1111-4c39-9b1a-aaaaaaaaaaaa";

    private TrackIngestService service;
    private LitemallTrackController controller;

    @BeforeEach
    void setup() {
        service = Mockito.mock(TrackIngestService.class);
        when(service.offerBatch(anyList(), any())).thenReturn(new TrackIngestService.IngestResult(1, 0));
        controller = new LitemallTrackController(service);
    }

    private LitemallTrackController.CollectBody batch() {
        TrackEventDto dto = new TrackEventDto();
        dto.setEventId(UUID.randomUUID().toString());
        dto.setType("page_view");
        dto.setPageType("home");
        LitemallTrackController.CollectBody body = new LitemallTrackController.CollectBody();
        body.setEvents(List.of(dto));
        return body;
    }

    @Test
    void collectWithoutEdgeVisitorHeaderIsSilentlyDropped() {
        controller.collect(batch(), null, null, null, "Mozilla/5.0", "en");
        verify(service, never()).offerBatch(anyList(), any());
    }

    @Test
    void collectWithMalformedVisitorHeaderIsSilentlyDropped() {
        controller.collect(batch(), "spoofed-visitor", null, null, null, null);
        verify(service, never()).offerBatch(anyList(), any());
    }

    @Test
    void collectDerivesContextFromHeadersOnly() {
        controller.collect(batch(), VISITOR, null, "fr", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0)", "fr-FR,fr;q=0.9");
        ArgumentCaptor<IngestContext> ctx = ArgumentCaptor.forClass(IngestContext.class);
        verify(service).offerBatch(anyList(), ctx.capture());
        assertThat(ctx.getValue().getVisitorId()).isEqualTo(VISITOR);
        assertThat(ctx.getValue().getCountryCode()).isEqualTo("FR");
        assertThat(ctx.getValue().getDeviceType()).isEqualTo("mobile");
        assertThat(ctx.getValue().getLocale()).isEqualTo("fr-FR");
    }

    @Test
    void unknownCountrySentinelIsNotStored() {
        controller.collect(batch(), VISITOR, null, "XX", null, null);
        ArgumentCaptor<IngestContext> ctx = ArgumentCaptor.forClass(IngestContext.class);
        verify(service).offerBatch(anyList(), ctx.capture());
        assertThat(ctx.getValue().getCountryCode()).isNull();
    }

    @Test
    void consentAcceptsOnlyKnownChoicesButNeverErrors() {
        LitemallTrackController.ConsentBody bad = new LitemallTrackController.ConsentBody();
        bad.setChoice("maybe");
        controller.consent(bad, VISITOR, null, null, null);
        verify(service, never()).offerConsent(anyString(), anyString(), anyLong(), any());

        LitemallTrackController.ConsentBody granted = new LitemallTrackController.ConsentBody();
        granted.setChoice("granted");
        granted.setOccurredAt(1722790000000L);
        controller.consent(granted, VISITOR, "DE", null, null);
        verify(service).offerConsent(Mockito.eq("granted"), Mockito.eq("analytics"),
                Mockito.eq(1722790000000L), any(IngestContext.class));
    }
}
