package org.linlinjava.litemall.order.infrastructure.services.acl.facades;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjTrackingSnapshot;
import org.linlinjava.litemall.order.infrastructure.services.cj.CjTokenService;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.CjTrackingFeignClient;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.tracking.CjTrackInfoResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The CJ {@code logistic/trackInfo} payload as CJ actually sends it (shape captured from a
 * live response 2026-11-21, values shortened): the per-hop {@code routes} and the last-mile
 * contact fields must survive deserialisation and reach the snapshot.
 */
class CjTrackingFacadeImplRoutesTest {

    private static final String LIVE_SHAPE = """
            {"code":200,"result":true,"message":"Success","data":[{
              "trackingNumber":"EQK861YQ","logisticName":"CJPacket Euro Sensitive F",
              "trackingFrom":"CN","trackingTo":"SE","customerOrderId":"SD1","paymentDate":null,
              "deliveryDay":"8","deliveryStartTime":null,"deliveryEndTime":null,
              "trackingStatus":"Arrived Courier Facility","lastTrackNumber":"BCMEQ861YQ",
              "cjMailNo":"CJP1","mailNo":null,
              "routes":[
                {"acceptAddress":"HEL","acceptTime":"2026-11-18 21:35:56","remark":"Released from customs"},
                {"acceptAddress":"CHINA","acceptTime":"2026-11-17 19:06:13","remark":"Departed from original airport"},
                {"acceptAddress":"","acceptTime":"2026-11-10 18:30:02","remark":"Label created."},
                {"acceptAddress":"X","acceptTime":"2026-11-10 18:00:00","remark":" "}
              ],
              "lastMileCarrier":"CityMail","lastMileCarrierPhone":"0771-40 60 40",
              "lastMileCarrierEmail":"mottagarservice@citymail.se"}],
             "requestId":"r","pointsInfo":{"total":1},"success":true}
            """;

    private CjTrackingSnapshot snapshotOf(String json) throws Exception {
        CjTrackingFeignClient client = mock(CjTrackingFeignClient.class);
        CjTokenService tokens = mock(CjTokenService.class);
        when(tokens.getValidToken()).thenReturn("tok");
        when(client.trackInfo("tok", "EQK861YQ"))
                .thenReturn(new ObjectMapper().readValue(json, CjTrackInfoResponse.class));
        return new CjTrackingFacadeImpl(client, tokens).trackInfo("EQK861YQ").orElseThrow();
    }

    @Test
    void routesAndLastMileContact_reachTheSnapshot_newestFirst_blankRemarksDropped() throws Exception {
        CjTrackingSnapshot s = snapshotOf(LIVE_SHAPE);

        assertThat(s.getTrackingStatus()).isEqualTo("Arrived Courier Facility");
        assertThat(s.getDeliveryTime()).isNull(); // absent from the live payload
        assertThat(s.getHops()).hasSize(3);
        assertThat(s.getHops().get(0))
                .isEqualTo(new CjTrackingSnapshot.Hop("2026-11-18 21:35:56", "HEL", "Released from customs"));
        assertThat(s.getLastMileCarrier()).isEqualTo("CityMail");
        assertThat(s.getLastTrackNumber()).isEqualTo("BCMEQ861YQ");
        assertThat(s.getLastMileCarrierPhone()).isEqualTo("0771-40 60 40");
        assertThat(s.getLastMileCarrierEmail()).isEqualTo("mottagarservice@citymail.se");
        assertThat(s.isDelivered()).isFalse();
    }

    @Test
    void payloadWithoutRoutes_yieldsAnEmptyHistory_notNull() throws Exception {
        CjTrackingSnapshot s = snapshotOf("""
                {"code":200,"result":true,"data":[{"trackingNumber":"EQK861YQ","trackingStatus":"In transit"}]}
                """);

        assertThat(s.getHops()).isEmpty();
    }
}
