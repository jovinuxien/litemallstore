package org.linlinjava.litemall.goods.infrastructure.messaging.sink;


import org.linlinjava.litemall.goods.domain.model.aggregates.LitemallGoodsAggregate;
import org.linlinjava.litemall.goods.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.goods.infrastructure.acl.ocs.OcsGoodsDocumentMapper;
import org.linlinjava.litemall.goods.infrastructure.acl.ocs.OcsIndexerClient;
import org.linlinjava.litemall.goods.infrastructure.configuration.RabbitMqConfig;
import org.linlinjava.litemall.goods.infrastructure.messaging.GoodsChangeMessage;
import org.linlinjava.litemall.goods.infrastructure.services.api.LitemallGoodsServiceApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Drives incremental OCS indexing. On every goods write
 * (add/update/delete) {@link org.linlinjava.litemall.goods.application
 * .LitemallGoodsManagementServiceImpl} publishes a {@link GoodsChangeMessage}
 * to Rabbit; this listener reads it, re-fetches the goods aggregate (for
 * UPSERT) and calls the OCS indexer ACL.
 */
@Component
public class MessageConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageConsumer.class);

    private final OcsIndexerClient indexerClient;
    private final OcsGoodsDocumentMapper mapper;
    private final LitemallGoodsServiceApi goodsServiceApi;

    public MessageConsumer(OcsIndexerClient indexerClient,
                           OcsGoodsDocumentMapper mapper,
                           LitemallGoodsServiceApi goodsServiceApi) {
        this.indexerClient = indexerClient;
        this.mapper = mapper;
        this.goodsServiceApi = goodsServiceApi;
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE_NAME)
    public void onGoodsChange(GoodsChangeMessage message) {
        if (message == null || message.getAction() == null || message.getGoodsId() == null) {
            LOGGER.warn("Discarding malformed GoodsChangeMessage: {}", message);
            return;
        }
        try {
            switch (message.getAction()) {
                case UPSERT:
                    LitemallGoodsAggregate goods = goodsServiceApi.getGoodsAggregateById(
                            new LitemallGoodsId(message.getGoodsId()));
                    if (goods == null) {
                        LOGGER.warn("UPSERT requested for missing goodsId={} — dropping", message.getGoodsId());
                        return;
                    }
                    indexerClient.upsert(mapper.toDocument(goods));
                    break;
                case DELETE:
                    indexerClient.delete(String.valueOf(message.getGoodsId()));
                    break;
            }
        } catch (Exception e) {
            // Don't poison the queue on transient OCS errors; let it retry.
            LOGGER.warn("OCS indexing failed for {}: {}", message, e.getMessage());
        }
    }
}
