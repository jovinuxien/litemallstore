package org.linlinjava.litemall.order;

//import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"org.linlinjava.litemall.db", "org.linlinjava.litemall.core"})
//@MapperScan("org.linlinjava.litemall.db.dao")
@EnableEurekaClient
@EnableFeignClients
//@EnableCircuitBreaker
public class LitemallOrderServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(LitemallOrderServiceApplication.class, args);
	}
}
