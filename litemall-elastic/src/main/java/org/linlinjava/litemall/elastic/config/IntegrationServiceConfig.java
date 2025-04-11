package org.linlinjava.litemall.elastic.config;

import org.linlinjava.litemall.db.dto.ElasticDto;
import org.linlinjava.litemall.elastic.service.ElasticMessageSource;
import org.linlinjava.litemall.elastic.service.ProductIndexingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageSource;
import org.springframework.messaging.MessageChannel;

import java.util.*;

@Configuration
public class IntegrationServiceConfig {

   /* @Autowired
    ProductIndexingService indexService;*/

   /* @Bean
    @Scope("prototype")
    ElasticDto elasticDto(){
        return new ElasticDto();
    }*/

  /*  public IntegrationServiceConfig(ProductIndexingService indexService) {
        this.indexService = indexService;
    }*/

    //@Bean
   /* public MessageSource<Map<String, Object>> elasticMessageSource(){
        return new ElasticMessageSource();
    }*/


   /* @Bean
    public MessageChannel inputChannel() {
        return new DirectChannel();
    }*/

    /*@Bean
    @SuppressWarnings("unused")
    public IntegrationFlow integrationFlows() {
        return IntegrationFlows.from(elasticMessageSource(),
                c -> c.poller(p -> p.fixedDelay(5000)))
                .transform(Transformers.toJson()) // toJson data
                .transform(this::jsonToElasticDtoToList)// making to list
                //.transform(jsonNodeTransformer())
                .split()
                //.transform(this::jsonNodeToElasticDto)
                .aggregate()
                //.handle(this::processElasticDtoList)
                //.transform(Transformers.toJson("elasticDto"))
                //.transform(new JsonToObjectTransformer())
                //.handle(indexService, "getGoods")
                .handle(System.out::println)
                .get();
    }*/


   /* @Bean
    public GenericTransformer<String, JsonNode> jsonNodeTransformer() {
       return json -> {
           try{
               return objectMapper().readTree(json);
           }catch (Exception e){
               throw new RuntimeException("Json parsing failed", e);
           }
       };
    }*/

    /*@Bean
    public ObjectMapper objectMapper(){
        return new ObjectMapper();
    }

    private ElasticDto jsonNodeToElasticDto(JsonNode jsonNode){
        ElasticDto dto = new ElasticDto();
        dto.setGoodsName(jsonNode.path("goodsName").asText());
        dto.setBrief(jsonNode.path("brief").asText());
        dto.setCounterPrice(jsonNode.path("counterPrice").decimalValue());
        dto.setRetailPrice(jsonNode.path("retailPrice").decimalValue());
        dto.setCategoryName(jsonNode.path("categoryName").asText());
        dto.setManufacturer(jsonNode.path("manufacturer").asText());
        dto.setAttribute(jsonNode.path("attribute").asText());
        dto.setParentCategory(jsonNode.path("parentCategory").asText());
        return dto;
    }*/

   /* private List<ElasticDto> jsonToElasticDtoToList(String json) {
        try {
            JsonNode jsonNode = objectMapper().readTree(json);
            List<ElasticDto> dtoList = new ArrayList<>();
            if (jsonNode.isArray()) {
                for (JsonNode node : jsonNode) {
                    dtoList.add(jsonNodeToElasticDto(node));
                }
            }
            return dtoList;
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert JSON to ElasticDto list", e);
        }
    }*/

    /*private List<ElasticDto> processElasticDtoList(List<ElasticDto> dtoList){
        return dtoList.stream()
                .filter(dto -> dto.getRetailPrice().intValue() < 300)
                .sorted(Comparator.comparing(ElasticDto::getRetailPrice))
                .collect(Collectors.toList());
    }*/
}


