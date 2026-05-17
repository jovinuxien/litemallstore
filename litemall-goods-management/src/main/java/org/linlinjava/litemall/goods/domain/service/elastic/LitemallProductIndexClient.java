package org.linlinjava.litemall.goods.domain.service.elastic;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.linlinjava.litemall.db.dao.LitemallBrandMapper;
import org.linlinjava.litemall.db.dao.LitemallGoodsMapper;
import org.linlinjava.litemall.db.domain.LitemallBrand;
import org.linlinjava.litemall.goods.domain.model.valueobjects.elastic.ProductDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LitemallProductIndexClient {

    @Autowired
    private LitemallGoodsMapper goodsMapper;

    @Autowired
    private LitemallBrandMapper brandMapper;

    private  LitemallProductIndexingService productIndexingService;

    public LitemallProductIndexClient(LitemallProductIndexingService productIndexingService) {
        this.productIndexingService = productIndexingService;
    }
    //public void indexProduct(Integer... goodsId){
    public void indexProduct(Integer... brandsId){
        // Fetch a single product from the database using the goodsId
        for (Integer brandId: brandsId) {

            //LitemallGoods goods = goodsMapper.selectByPrimaryKey(goodId);
            LitemallBrand brand = brandMapper.selectByPrimaryKey(brandId);

            if (brand != null) {
                // Create a ProductDocument for the product
                ProductDocument productDocument = productIndexingService.createProductDocumentByBrand(brand);

                ObjectMapper objectMapper = new ObjectMapper();

                try {
                    // Convert the ProductDocument object to a JSON string
                    String jsonString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(productDocument);
                    System.out.println("The product document is: " + jsonString);
                } catch (JsonProcessingException e) {
                    System.out.println("Error converting ProductDocument to json " + e.getMessage());
                }
                //System.out.println("the product document is: " + productDocument);

                // Index the ProductDocument in Elasticsearch
                // You can use the ElasticsearchOperations provided by the Elasticsearch client
                // For example:
                // elasticsearchOperations.index(IndexRequest.builder("products").source(objectMapper.writeValueAsString(productDocument)).build());
                // Replace the above line with your actual Elasticsearch indexing logic

            } else {
                System.out.println("Product with Id " + brandId + " not found");
            }
        }
    }
}
