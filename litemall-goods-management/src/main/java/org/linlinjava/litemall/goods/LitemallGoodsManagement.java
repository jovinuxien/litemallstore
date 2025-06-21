package org.linlinjava.litemall.goods;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.messaging.Source;



@SpringBootApplication(scanBasePackages = {"org.linlinjava.litemall.db", "org.linlinjava.litemall.db.dao", "org.linlinjava.litemall.core", "org.linlinjava.litemall.goods"})
//@MapperScan("org.linlinjava.litemall.db.dao")
@EnableEurekaClient
//@EnableCircuitBreaker
//@EnableBinding(Source.class) //This way of binding is deprecated
public class LitemallGoodsManagement {
	public static void main(String[] args) {
		SpringApplication.run(LitemallGoodsManagement.class, args);
	}
}
