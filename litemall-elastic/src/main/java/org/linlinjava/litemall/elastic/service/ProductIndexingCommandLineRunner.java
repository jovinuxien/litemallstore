package org.linlinjava.litemall.elastic.service;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;


@Component
// This class will be executed when the application starts
public class ProductIndexingCommandLineRunner implements CommandLineRunner {

    private final ProductIndexClient productIndexClient;


    public ProductIndexingCommandLineRunner(ProductIndexClient client){
        this.productIndexClient = client;
    }

   public void run(String... args) {
       // Run the indexProduct with the sample goodsId
       // This following goodsId does not have brand's id
       //Integer sampleGoodsId =  1009024;
       //Integer sampleGoodsId =  1011004;
       productIndexClient.indexProduct(1001020);
   }
}
