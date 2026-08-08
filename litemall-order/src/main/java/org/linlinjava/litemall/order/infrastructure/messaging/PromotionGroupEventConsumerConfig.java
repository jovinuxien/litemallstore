package org.linlinjava.litemall.order.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.litemall.order.application.internal.GroupExpiredOrderProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Wave 21: order's first inbound Kafka binding — consumes promotion's
 * {@code GROUP_EXPIRED} events ({@code PromotionEventStreamBridge} publishes them
 * to the dynamic destination {@code litemall.promotion.group.expired.v1}; the
 * binding lives in {@code config/application.yml} under
 * {@code spring.cloud.function.definition=groupExpired} /
 * {@code groupExpired-in-0}).
 *
 * <p>Payload (additive Wave-21 contract): the base event fields plus
 * {@code memberPinkIds[]} — every slot of the failed group. Parsed LENIENTLY with
 * a local plain ObjectMapper (the shared core JacksonConfig rejects unknown
 * fields): ints or {@code {id:N}} VOs both accepted, and when {@code
 * memberPinkIds} is absent (an old payload) the leader's {@code groupPinkId}
 * still gets processed. Everything is caught: a poison message is logged and
 * dropped, never an infinite redelivery loop on the money path — the processor's
 * per-order idempotency makes replays safe.
 */
@Configuration
public class PromotionGroupEventConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(PromotionGroupEventConsumerConfig.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    public Consumer<Message<byte[]>> groupExpired(GroupExpiredOrderProcessor processor) {
        return message -> {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            try {
                JsonNode root = objectMapper.readTree(payload);
                Set<Integer> pinkIds = extractPinkIds(root);
                if (pinkIds.isEmpty()) {
                    log.warn("GROUP_EXPIRED event carried no pink ids — nothing to do: {}", payload);
                    return;
                }
                log.info("GROUP_EXPIRED received for group pink {} ({} slot(s))",
                        root.path("groupPinkId").path("id").asText("?"), pinkIds.size());
                processor.processExpiredGroup(pinkIds);
            } catch (Exception e) {
                // Poison / unparseable message: log and drop. The processor itself
                // never throws, so this only guards deserialization.
                log.error("Failed to process GROUP_EXPIRED event payload: {}", payload, e);
            }
        };
    }

    /** memberPinkIds[] (ints or {id} objects), plus groupPinkId as a fallback/extra. */
    private Set<Integer> extractPinkIds(JsonNode root) {
        Set<Integer> pinkIds = new LinkedHashSet<>();
        for (JsonNode member : root.path("memberPinkIds")) {
            int id = member.isNumber() ? member.asInt() : member.path("id").asInt(0);
            if (id > 0) {
                pinkIds.add(id);
            }
        }
        // The leader's own slot: present in memberPinkIds per the Wave-21 contract,
        // but old/partial payloads may only carry groupPinkId — cover both.
        int leader = root.path("groupPinkId").isNumber()
                ? root.path("groupPinkId").asInt()
                : root.path("groupPinkId").path("id").asInt(0);
        if (leader > 0) {
            pinkIds.add(leader);
        }
        return pinkIds;
    }
}
