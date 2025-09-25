package org.linlinjava.litemall.goods.infrastructure.configuration;


import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE_NAME = "appExchange";
    public static final String QUEUE_NAME = "appQueue";
    public static final String ROUTING_KEY = "messages.key";


    @Bean
    public TopicExchange appExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public Queue appQueue() {
        return new Queue(QUEUE_NAME);
    }

    @Bean
    public Binding declareBinding() {
        return BindingBuilder.bind(appQueue())
                .to(appExchange())
                .with(ROUTING_KEY);
    }

}
