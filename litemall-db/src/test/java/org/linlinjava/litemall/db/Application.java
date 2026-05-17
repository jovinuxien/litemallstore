package org.linlinjava.litemall.db;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.test.context.ActiveProfiles;

@SpringBootApplication(scanBasePackages = {"org.linlinjava.litemall.db"})
@MapperScan("org.linlinjava.litemall.db.dao")
@ActiveProfiles("test")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}