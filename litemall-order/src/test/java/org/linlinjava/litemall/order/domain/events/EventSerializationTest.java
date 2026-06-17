package org.linlinjava.litemall.order.domain.events;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.order.domain.events.coupon.LitemallCouponUsedEvent;
import org.linlinjava.litemall.order.domain.events.groupon.LitemallGrouponParticipatedEvent;
import org.linlinjava.litemall.order.domain.events.groupon.LitemallGrouponSucceededEvent;
import org.linlinjava.litemall.order.domain.events.order.LitemallOrderCreatedEvent;
import org.linlinjava.litemall.order.domain.events.payment.LitemallOrderPaymentSuccessEvent;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.coupon.LitemallCouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.groupon.LitemallGrouponId;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class EventSerializationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
                .setVisibility(com.fasterxml.jackson.annotation.PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                .setVisibility(com.fasterxml.jackson.annotation.PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE)
                .setVisibility(com.fasterxml.jackson.annotation.PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE);
    }

    @Test
    void orderCreatedEvent_serializesWithEnrichedFields() throws Exception {
        LitemallOrderCreatedEvent event = new LitemallOrderCreatedEvent(
                new LitemallOrderId(42),
                new LitemallMoney(new BigDecimal("99.99")),
                7, "ORD-7-42");
        event.setCorrelationId("corr-abc");

        String json = mapper.writeValueAsString(event);

        assertThat(json).contains("\"orderSn\":\"ORD-7-42\"");
        assertThat(json).contains("\"userId\":7");
        assertThat(json).contains("\"correlationId\":\"corr-abc\"");
        assertThat(json).contains("\"schemaVersion\":\"1\"");
        assertThat(json).contains("\"occurredOn\":");
    }

    @Test
    void paymentSuccessEvent_serializes() throws Exception {
        LitemallOrderPaymentSuccessEvent event = new LitemallOrderPaymentSuccessEvent(
                new LitemallOrderId(99),
                new LitemallMoney(new BigDecimal("15.00")),
                LocalDateTime.now());
        String json = mapper.writeValueAsString(event);
        assertThat(json).contains("\"schemaVersion\":\"1\"");
        assertThat(json).contains("\"paidAmount\":");
    }

    @Test
    void grouponParticipatedEvent_serializes() throws Exception {
        LitemallGrouponParticipatedEvent event = new LitemallGrouponParticipatedEvent(
                new LitemallGrouponId(5),
                new LitemallOrderId(42), 7, LocalDateTime.now());
        event.setCorrelationId("corr-xyz");
        String json = mapper.writeValueAsString(event);
        assertThat(json).contains("\"correlationId\":\"corr-xyz\"");
        assertThat(json).contains("\"participantUserId\":7");
    }

    @Test
    void grouponSucceededEvent_serializes() throws Exception {
        LitemallGrouponSucceededEvent event = new LitemallGrouponSucceededEvent(new LitemallGrouponId(5));
        String json = mapper.writeValueAsString(event);
        assertThat(json).contains("\"schemaVersion\":\"1\"");
    }

    @Test
    void couponUsedEvent_serializes() throws Exception {
        LitemallCouponUsedEvent event = new LitemallCouponUsedEvent(
                new LitemallCouponId(11),
                new LitemallOrderId(42), 7,
                new LitemallMoney(new BigDecimal("5.00")));
        String json = mapper.writeValueAsString(event);
        assertThat(json).contains("\"schemaVersion\":\"1\"");
        assertThat(json).contains("\"userId\":7");
    }
}
