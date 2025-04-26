package org.linlinjava.litemall.order.infrastructure.messaging.source;

import org.linlinjava.litemall.order.infrastructure.messaging.model.OrderServiceChangeModel;
import org.linlinjava.litemall.order.utils.ActionEnum;
import org.linlinjava.litemall.order.utils.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
public class SimpleSourceBean {


    private Source source;

    private static final Logger logger = LoggerFactory.getLogger(SimpleSourceBean.class);

    public SimpleSourceBean(Source source){
        this.source = source;
    }
    public void publishOrganizationChange(ActionEnum action, String organizationId){
        logger.debug("Sending Kafka message {} for Organization Id: {}", action, organizationId);
        OrderServiceChangeModel change =  new OrderServiceChangeModel(
                OrderServiceChangeModel.class.getTypeName(),
                action.toString(),
                organizationId,
                UserContext.getCorrelationId());

        source.output().send(MessageBuilder.withPayload(change).build());
    }

}
