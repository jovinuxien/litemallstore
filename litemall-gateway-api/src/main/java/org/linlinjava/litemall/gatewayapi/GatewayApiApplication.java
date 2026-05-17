package org.linlinjava.litemall.gatewayapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Customer edge gateway (Spring Cloud Gateway).
 *
 * <p>Customer edge: self-signed customer JWT issued and validated here (no
 * Keycloak, no TokenRelay). Scans {@code org.linlinjava.litemall.db} to reuse
 * the customer store ({@code LitemallUserService}), the V15 refresh-token
 * mapper and the edge JWT toolkit ({@code db.auth}); litemall-core is
 * deliberately NOT scanned (it drags servlet Spring MVC).</p>
 */
@SpringBootApplication(scanBasePackages = {
        "org.linlinjava.litemall.gatewayapi",
        "org.linlinjava.litemall.db"})
public class GatewayApiApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayApiApplication.class);

    public static void main(String[] args) {
        Environment env = SpringApplication.run(GatewayApiApplication.class, args).getEnvironment();
        String port = env.getProperty("server.port");
        String host = "localhost";
        try {
            host = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            LOGGER.warn("Host name could not be determined, using `localhost` as fallback");
        }
        LOGGER.info(
                "\n----------------------------------------------------------\n\t" +
                        "Application '{}' is running! Access URLs:\n\t" +
                        "Local: \t\thttp://localhost:{}\n\t" +
                        "External: \thttp://{}:{}\n\t" +
                        "Profile(s): \t{}\n----------------------------------------------------------",
                env.getProperty("spring.application.name"),
                port,
                host,
                port,
                env.getActiveProfiles().length == 0 ? env.getDefaultProfiles() : env.getActiveProfiles());
    }
}