package org.linlinjava.litemall.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
public class PaymentServiceNotification {
	public static void main(String[] args) {
		SpringApplication.run(PaymentServiceNotification.class, args);
	}

}
