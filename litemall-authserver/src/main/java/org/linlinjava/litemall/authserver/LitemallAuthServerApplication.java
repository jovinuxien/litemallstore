package org.linlinjava.litemall.authserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;

/**
 * Service-to-service OAuth2 Authorization Server.
 *
 * <p>Issues only {@code client_credentials} machine tokens (RS256, self-signed,
 * JWKS-published) for gateway-&gt;service and service-&gt;service calls. There is
 * NO end-user login here — customer/admin identity is minted at the edge
 * gateways and forwarded as trusted {@code X-User-*} headers, accepted by a
 * service only when accompanied by a valid machine token from this server.
 */
@SpringBootApplication
public class LitemallAuthServerApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(LitemallAuthServerApplication.class);

    public static void main(String[] args) {
        Environment env = SpringApplication.run(LitemallAuthServerApplication.class, args).getEnvironment();
        LOGGER.info(
                "\n----------------------------------------------------------\n\t" +
                        "Application '{}' is running! Token endpoint: \thttp://localhost:{}/oauth2/token\n\t" +
                        "JWKS: \thttp://localhost:{}/oauth2/jwks\n\t" +
                        "Profile(s): \t{}\n----------------------------------------------------------",
                env.getProperty("spring.application.name"),
                env.getProperty("server.port"),
                env.getProperty("server.port"),
                env.getActiveProfiles().length == 0 ? env.getDefaultProfiles() : env.getActiveProfiles());
    }
}
