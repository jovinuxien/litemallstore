package org.linlinjava.litemall.admin.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static io.swagger.v3.oas.annotations.enums.SecuritySchemeIn.HEADER;

/**
 * swagger在线文档配置<br>
 * 项目启动后可通过地址：http://host:ip/swagger-ui.html 查看在线文档
 *
 * @author enilu
 * @version 2018-07-24
 */


@OpenAPIDefinition(info = @io.swagger.v3.oas.annotations.info.Info(title = "Litemall Admin API", version = "v1"))
@SecurityScheme(
        name="apiKey",
        type =  SecuritySchemeType.HTTP,
        paramName = "X-Litemall-Token",
        in = HEADER

        //config with bearer
        //name = "apiKeyQuery",
        //type = SecuritySchemeType.APIKEY,
        //paramName = "api_key",
        //in = SecurityScheme.In.QUERY

        //name = "bearerAuth",
        //type = SecuritySchemeType.HTTP,
        //bearerFormat = "jwt",
        //scheme = "bearer"
)
@Configuration
public class AdminSwagger2Configuration {
    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin")
                .pathsToMatch("/admin/**")
                .packagesToScan("org.linlinjava.litemall.admin.web")
                .build();
    }

    @Bean
    public OpenAPI adminApiInfo() {
        return new OpenAPI()
                .info(new Info().title("litemall-admin API")
                        .description("litemall管理后台API")
                        .version("v0.0.1")
                        .license(new License().name("Apache 2.0").url("https://github.com/linlinjava/litemall")))
                        .externalDocs(new ExternalDocumentation()
                          .description("litemall Wiki Documentation")
                          .url("https://github.com/linlinjava/litemall"));
    }
}
