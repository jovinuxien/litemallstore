package org.linlinjava.litemall.goods.infrastructure.messaging.sink;


import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.goods.domain.events.GoodsIndexEvent;
import org.linlinjava.litemall.goods.domain.model.valueobjects.elastic.ProductDocument;
import org.linlinjava.litemall.goods.domain.service.elastic.LitemallProductIndexingService;
import org.linlinjava.litemall.goods.domain.service.elastic.ProductIndexer;
import org.linlinjava.litemall.goods.infrastructure.configuration.RabbitMqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class MessageConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageConsumer.class);

    private final LitemallGoodsService goodsService;
    private final LitemallProductIndexingService indexingService;
    private final ProductIndexer productIndexer;

    public MessageConsumer(LitemallGoodsService goodsService,
                           LitemallProductIndexingService indexingService,
                           ProductIndexer productIndexer) {
        this.goodsService = goodsService;
        this.indexingService = indexingService;
        this.productIndexer = productIndexer;
    }

    @RabbitListener(queues = RabbitMqConfig.GOODS_INDEX_QUEUE)
    public void onGoodsIndexEvent(GoodsIndexEvent event) {
        if (event == null || event.getGoodsId() == null || event.getAction() == null) {
            LOGGER.warn("Discarding malformed goods index event: {}", event);
            return;
        }
        switch (event.getAction()) {
            case UPSERT -> {
                LitemallGoods goods = goodsService.findById(event.getGoodsId());
                // Index invariant: only on-sale goods live in litemall_index (reindexAll
                // filters the same way), so a missing or off-sale goods leaves the index.
                if (goods == null || !Boolean.TRUE.equals(goods.getIsOnSale())) {
                    LOGGER.info("UPSERT for missing/off-sale goods id={}, removing from index", event.getGoodsId());
                    productIndexer.delete(String.valueOf(event.getGoodsId()));
                    return;
                }
                ProductDocument doc = indexingService.createProductDocument(goods);
                productIndexer.upsert(doc);
            }
            case DELETE -> productIndexer.delete(String.valueOf(event.getGoodsId()));
        }
    }
}
