package org.linlinjava.litemall.wx.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.springdoc.core.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * swagger在线文档配置<br>
 * 项目启动后可通过地址：http://host:ip/swagger-ui.html 查看在线文档
 *
 * @author enilu
 * @version 2018-07-24
 */

@OpenAPIDefinition(
        info = @Info(
                title = "Litemall API",
                version = "1.0",
                description = "Litemall API documentation",
                contact = @Contact(name = "Jovi", email = "https://github.com/linlinjava/litemall")
        )
)
@Configuration
public class WxSwagger2Configuration {
    @Bean
    public GroupedOpenApi wxApi(){
        return GroupedOpenApi.builder()
               .group("wx")
                .pathsToMatch("/wx/**")
               .build();
    }

}
