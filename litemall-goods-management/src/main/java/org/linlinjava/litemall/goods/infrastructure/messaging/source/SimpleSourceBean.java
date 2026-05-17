package org.linlinjava.litemall.goods.infrastructure.messaging.source;


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
