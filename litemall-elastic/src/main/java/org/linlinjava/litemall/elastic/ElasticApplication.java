package org.linlinjava.litemall.elastic;

import org.linlinjava.litemall.elastic.service.ProductIndexingService;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.Map;

@SpringBootApplication(scanBasePackages = {"org.linlinjava.litemall.db", "org.linlinjava.litemall.core", "org.linlinjava.litemall.elastic"})
@MapperScan("org.linlinjava.litemall.db.dao")
@EnableTransactionManagement
@EnableScheduling
public class ElasticApplication {

    public static void main(String[] args) {
        SpringApplication.run(ElasticApplication.class, args);
    }

    /*@Bean
    CommandLineRunner initIndex(ProductIndexingService indexService){
       return args -> {
           Map<String, Object> object =  indexService.getGoods("MUJI manufacturer", "material");

           System.out.println("Elastic index created and goods inserted successfully" + object.get("list") + "\n" + " Fected time :" + object.get("fetched time"));
       };
    }
*/
}
