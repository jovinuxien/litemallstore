package org.linlinjava.litemall.goods.infrastructure.messaging.source;

import org.linlinjava.litemall.goods.infrastructure.messaging.model.GoodsServiceChangeModel;
import org.linlinjava.litemall.goods.utils.ActionEnum;
import org.linlinjava.litemall.goods.utils.UserContext;
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
    public void publishOrganizationChange(ActionEnum action, String goodsServiceId){
        logger.debug("Sending Kafka message {} for Organization Id: {}", action, goodsServiceId);
        GoodsServiceChangeModel change =  new GoodsServiceChangeModel(
                GoodsServiceChangeModel.class.getTypeName(),
                action.toString(),
                goodsServiceId,
                UserContext.getCorrelationId());

        source.output().send(MessageBuilder.withPayload(change).build());
    }

}
