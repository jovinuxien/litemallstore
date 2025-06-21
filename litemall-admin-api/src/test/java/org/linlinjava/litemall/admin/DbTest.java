package org.linlinjava.litemall.admin;

import org.junit.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;

@WebAppConfiguration
@ExtendWith(SpringExtension.class)
@SpringBootTest
/*@TestPropertySource(
        locations = "classpath:application.yml",
        properties = {
                "spring.profiles.active=db,core,admin",
                "spring.config.import=optional:classpath:application-db.yml"
        }
)*/
public class DbTest {
    @Autowired
    private Environment environment;

    @Test
    public void test() {
        String user = environment.getProperty("spring.datasource.druid.username");
        String password = environment.getProperty("spring.datasource.druid.password");
        String url = environment.getProperty("spring.datasource.druid.url");
        int index1 = url.indexOf("3306/");
        int index2 = url.indexOf("?");
        String db = url.substring(index1+5, index2);
        System.out.println(user);
        System.out.println(password);
        System.out.println(db);
    }

    @Test
    public void testFileCreate() throws IOException {
        LocalDate localDate = LocalDate.now();
        String fileName = localDate.toString() + ".sql";
        System.out.println(fileName);

        File file = new File("backup", fileName);
        file.getParentFile().mkdirs();
        file.createNewFile();
    }

    @Test
    public void testFileDelete() throws IOException {
        LocalDate localDate = LocalDate.now();
        String fileName = localDate.toString() + ".sql";
        System.out.println(fileName);

        File file = new File("backup", fileName);
        file.deleteOnExit();
    }
}
