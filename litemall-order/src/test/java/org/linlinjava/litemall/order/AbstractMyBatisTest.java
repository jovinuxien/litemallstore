package org.linlinjava.litemall.order;

import org.junit.jupiter.api.extension.ExtendWith;
import org.linlinjava.litemall.order.application.config.TestContainerSpringContextCustomizerFactory;
import org.linlinjava.litemall.order.application.config.TestMyBatisConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.junit.jupiter.Testcontainers;


///@ExtendWith(SpringExtension.class)
@Testcontainers
@SpringBootTest(classes = {TestMyBatisConfig.class})
@ActiveProfiles("test")
@TestContainerSpringContextCustomizerFactory.MyBatisTestContainers
public class AbstractMyBatisTest {
}
