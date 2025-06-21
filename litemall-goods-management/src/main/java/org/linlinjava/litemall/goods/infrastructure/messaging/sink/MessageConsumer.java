package org.linlinjava.litemall.goods.infrastructure.messaging.sink;


import org.linlinjava.litemall.goods.infrastructure.configuration.RabbitMqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class MessageConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageConsumer.class);

    //@RabbitListener(queues = "${spring.rabbitmq.queue.name}")
    @RabbitListener(queues = RabbitMqConfig.QUEUE_NAME)
    public void receiveMessage(String message){
        LOGGER.info("Received message from RabbitMQ: {}", message);
    }
}
