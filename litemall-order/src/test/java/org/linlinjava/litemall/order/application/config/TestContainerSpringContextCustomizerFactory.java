package org.linlinjava.litemall.order.application.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;
import org.testcontainers.containers.PostgreSQLContainer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;


@Configuration
public class TestContainerSpringContextCustomizerFactory implements ContextCustomizerFactory {

    private static final Logger log = LoggerFactory.getLogger(TestContainerSpringContextCustomizerFactory.class);

    private static PostgreSQLContainer<?> postgresqlContainer;

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MyBatisTestContainers {
    }

    @Override
    public ContextCustomizer createContextCustomizer(Class<?> testClass, List<ContextConfigurationAttributes> configAttributes) {
        return (ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) -> {
            ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
            TestPropertyValues testValues = TestPropertyValues.empty();

            AnnotatedElementUtils.findAllMergedAnnotations(testClass, MyBatisTestContainers.class);
            log.info("Setting up MyBatis Testcontainers for {}", testClass.getName());

            if (postgresqlContainer == null) {
                postgresqlContainer = new PostgreSQLContainer<>("postgres:13")
                        .withDatabaseName("testdb")
                        .withUsername("test")
                        .withPassword("test");
                postgresqlContainer.start();
            }

            testValues = testValues.and(
                    "spring.datasource.url=" + postgresqlContainer.getJdbcUrl(),
                    "spring.datasource.username=" + postgresqlContainer.getUsername(),
                    "spring.datasource.password=" + postgresqlContainer.getPassword(),
                    "spring.datasource.driver-class-name=" + postgresqlContainer.getDriverClassName(),

                    // MyBatis specific properties
                    "mybatis.mapper-locations=classpath*:mapper/**/*.xml",
                    "mybatis.type-aliases-package=org.linlinjava.litemall.db.domain",
                    "mybatis.configuration.map-underscore-to-camel-case=true"
            );

            testValues.applyTo(context);

        };
    }
}
