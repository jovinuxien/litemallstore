package org.linlinjava.litemall.goods.domain.service.elastic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;

import java.util.HashMap;
import java.util.Map;

//public class ElasticMessageSource implements MessageSource<Map<String, Object>> {
public class ElasticMessageSource {

    //@Autowired
    //private ProductIndexingService indexService;

    /*public Message<Map<String, Object>> receive() {
        Map<String, Object> data = new HashMap<>();
        data = indexService.getGoods("MUJI manufacturer", "material");
       return MessageBuilder.withPayload(data).build();
    }*/
}
