package org.linlinjava.litemall.order.application.config.interfaces;

public interface MyBatisTestContainer {
    void start();
    void stop();
    String getJdbcUrl();
    String getUsername();
    String getPassword();
    String getDriverClassName();
}
