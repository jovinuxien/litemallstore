package org.linlinjava.litemall.order.infrastructure.messaging.source;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SimpleSourceBean {

    private final StreamBridge streamBridge;


    private static final Logger logger = LoggerFactory.getLogger(SimpleSourceBean.class);

    public SimpleSourceBean(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }
    /*public void publishOrganizationChange(ActionEnum action, String organizationId){
        logger.debug("Sending Kafka message {} for Organization Id: {}", action, organizationId);
        OrderServiceChangeModel change =  new OrderServiceChangeModel(
                OrderServiceChangeModel.class.getTypeName(),
                action.toString(),
                organizationId,
                UserContext.getCorrelationId());

        source.output().send(MessageBuilder.withPayload(change).build());
    }
*/
    public void publishMessage(String action, String message) {
        Map<String, Object> headers = Map.of("action", action);
        Message<String> msg = MessageBuilder.withPayload(message)
                .copyHeaders(headers)
                .build();
        streamBridge.send("output-out-0", msg);
    }

}
