package org.linlinjava.litemall.goods.infrastructure.configuration;


import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Single messaging backend for this module: Spring AMQP / RabbitMQ.
 *
 * <p>Two flows share the {@link #EXCHANGE_NAME} topic exchange:
 * <ul>
 *   <li><b>Incremental OCS indexing</b> — goods writes publish a {@code GoodsIndexEvent}
 *       (JSON) with {@link #GOODS_INDEX_ROUTING_KEY} to {@link #GOODS_INDEX_QUEUE}; the
 *       {@code MessageConsumer} upserts/deletes the OCS document. This is the live path.</li>
 *   <li><b>Legacy demo queue</b> — {@link #QUEUE_NAME} bound on {@link #ROUTING_KEY}; kept so
 *       existing references compile. New work should use the goods-index flow above.</li>
 * </ul>
 */
@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE_NAME = "appExchange";

    // Incremental OCS indexing flow (GoodsIndexEvent -> MessageConsumer).
    public static final String GOODS_INDEX_QUEUE = "goods.index.queue";
    public static final String GOODS_INDEX_ROUTING_KEY = "goods.index";

    // Legacy demo queue (kept for backward compatibility of existing references).
    public static final String QUEUE_NAME = "appQueue";
    public static final String ROUTING_KEY = "messages.key";


    @Bean
    public TopicExchange appExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public Queue goodsIndexQueue() {
        return new Queue(GOODS_INDEX_QUEUE);
    }

    @Bean
    public Binding goodsIndexBinding() {
        return BindingBuilder.bind(goodsIndexQueue())
                .to(appExchange())
                .with(GOODS_INDEX_ROUTING_KEY);
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

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
