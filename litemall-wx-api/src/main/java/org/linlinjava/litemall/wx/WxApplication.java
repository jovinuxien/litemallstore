package org.linlinjava.litemall.wx;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication(scanBasePackages = {"org.linlinjava.litemall.db", "org.linlinjava.litemall.core", "org.linlinjava.litemall.wx"})
@MapperScan("org.linlinjava.litemall.db.dao")
@EnableTransactionManagement
@EnableScheduling
public class WxApplication {

    public static void main(String[] args) {
        SpringApplication.run(WxApplication.class, args);
    }

}