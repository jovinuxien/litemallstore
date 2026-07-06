package org.linlinjava.litemall.goods.infrastructure.messaging.source;

import org.linlinjava.litemall.goods.domain.events.GoodsIndexEvent;
import org.linlinjava.litemall.goods.infrastructure.configuration.RabbitMqConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class GoodsIndexEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public GoodsIndexEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(GoodsIndexEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.EXCHANGE_NAME,
                RabbitMqConfig.GOODS_INDEX_ROUTING_KEY,
                event);
    }
}
