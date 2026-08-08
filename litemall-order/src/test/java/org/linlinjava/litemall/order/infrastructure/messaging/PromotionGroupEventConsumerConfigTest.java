package org.linlinjava.litemall.order.infrastructure.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.order.application.internal.GroupExpiredOrderProcessor;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Deserialization of promotion's GROUP_EXPIRED payload
 * ({@code litemall.promotion.group.expired.v1}): the additive Wave-21
 * {@code memberPinkIds[]} is read leniently (raw ints or {@code {id}} VOs), the
 * leader's {@code groupPinkId} is always covered (old payloads carry only it),
 * and a poison message is logged + dropped — never rethrown into redelivery.
 */
@ExtendWith(MockitoExtension.class)
class PromotionGroupEventConsumerConfigTest {

    @Mock
    private GroupExpiredOrderProcessor processor;

    private Consumer<Message<byte[]>> consumer() {
        return new PromotionGroupEventConsumerConfig().groupExpired(processor);
    }

    private static Message<byte[]> message(String json) {
        return MessageBuilder.withPayload(json.getBytes(StandardCharsets.UTF_8)).build();
    }

    @SuppressWarnings("unchecked")
    private Collection<Integer> capturedPinkIds() {
        ArgumentCaptor<Collection<Integer>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(processor).processExpiredGroup(captor.capture());
        return captor.getValue();
    }

    @Test
    void wave21Payload_memberPinkIdsAsIntsOrVos_allProcessed() {
        consumer().accept(message(
                "{\"eventName\":\"GROUP_EXPIRED\",\"schemaVersion\":\"1\","
                        + "\"groupPinkId\":{\"id\":3},\"combinationId\":{\"id\":12},"
                        + "\"memberCount\":2,\"memberPinkIds\":[{\"id\":3},4]}"));

        assertEquals(Set.of(3, 4), Set.copyOf(capturedPinkIds()));
    }

    @Test
    void legacyPayload_withoutMemberPinkIds_stillProcessesTheLeaderSlot() {
        consumer().accept(message(
                "{\"eventName\":\"GROUP_EXPIRED\",\"schemaVersion\":\"1\","
                        + "\"groupPinkId\":{\"id\":9},\"combinationId\":{\"id\":12},\"memberCount\":1}"));

        assertEquals(List.of(9), List.copyOf(capturedPinkIds()));
    }

    @Test
    void poisonMessage_isDroppedNotRethrown() {
        assertDoesNotThrow(() -> consumer().accept(message("this is not json")));
        verify(processor, never()).processExpiredGroup(any());
    }

    @Test
    void payloadWithoutAnyPinkIds_isANoOp() {
        consumer().accept(message("{\"eventName\":\"GROUP_EXPIRED\"}"));
        verify(processor, never()).processExpiredGroup(any());
    }
}
