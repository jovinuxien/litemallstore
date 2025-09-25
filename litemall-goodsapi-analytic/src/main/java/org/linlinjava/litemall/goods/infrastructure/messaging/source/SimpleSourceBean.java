package org.linlinjava.litemall.goods.infrastructure.messaging.source;


import org.linlinjava.litemall.goods.domain.model.valueobjects.LitemallGoodsId;
import org.linlinjava.litemall.goods.infrastructure.messaging.model.GoodsServiceChangeModel;
import org.linlinjava.litemall.goods.utils.ActionEnum;
import org.linlinjava.litemall.goods.utils.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

//@Component
public class SimpleSourceBean {

   /* @Autowired
    private final Source source;

    private static final Logger logger = LoggerFactory.getLogger(SimpleSourceBean.class);

    @Autowired
    public SimpleSourceBean(Source source) {
        this.source = source;
    }

    public void publishGoodsChange(ActionEnum actionEnum, LitemallGoodsId litemallGoodsId) {
        logger.debug("Publishing Kafka message {} for Goods Id: {}", actionEnum, litemallGoodsId);
        GoodsServiceChangeModel changeModel = new GoodsServiceChangeModel(GoodsServiceChangeModel.class.getTypeName(),
                actionEnum.toString(), litemallGoodsId, UserContext.getCorrelationId());

        source.output().send(MessageBuilder.withPayload(changeModel).build());
    }*/
}
