package org.linlinjava.litemall.goods.infrastructure.configuration;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@EnableFeignClients(basePackages = "org.linlinjava.litemall.goods")
public class LitemallGoodsFeignConfiguration {
}
