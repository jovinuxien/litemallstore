package org.linlinjava.litemall;

import javax.servlet.annotation.WebServlet;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication(scanBasePackages = {"org.linlinjava.litemall"})
@MapperScan("org.linlinjava.litemall.db.dao")
@EnableTransactionManagement
@EnableScheduling
@WebServlet
public class Application extends javax.servlet.http.HttpServlet {

    public static void main(String[] args) throws Exception {
        SpringApplication.run(Application.class, args);
    }
    /*protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(Application.class);
    }*/
}