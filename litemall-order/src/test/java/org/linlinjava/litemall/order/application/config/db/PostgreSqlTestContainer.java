package org.linlinjava.litemall.order.application.config.db;

import org.linlinjava.litemall.order.application.config.interfaces.MyBatisTestContainer;
import org.testcontainers.containers.PostgreSQLContainer;

public class PostgreSqlTestContainer implements MyBatisTestContainer {

    private PostgreSQLContainer<?> postgreSQLContainer;

    public PostgreSqlTestContainer() {
        this.postgreSQLContainer = new PostgreSQLContainer<>("postgres:13")
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test");
    }

    @Override
    public void start() {
         postgreSQLContainer.start();
    }

    @Override
    public void stop() {
       postgreSQLContainer.stop();
    }

    @Override
    public String getJdbcUrl() {
        return postgreSQLContainer.getJdbcUrl();
    }

    @Override
    public String getUsername() {
        return postgreSQLContainer.getUsername();
    }

    @Override
    public String getPassword() {
        return postgreSQLContainer.getPassword();
    }

    @Override
    public String getDriverClassName() {
        return postgreSQLContainer.getDriverClassName();
    }
}
