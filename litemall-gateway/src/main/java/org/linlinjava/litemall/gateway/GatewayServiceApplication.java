package org.linlinjava.litemall.gateway;

import jakarta.annotation.PostConstruct;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tech.jhipster.config.DefaultProfileUtil;
import tech.jhipster.config.JHipsterConstants;
import tech.jhipster.config.JHipsterProperties;

//import javax.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

@SpringBootApplication(exclude = {org.springframework.cloud.security.oauth2.gateway.TokenRelayAutoConfiguration.class})
@EnableEurekaClient
@RestController
@EnableConfigurationProperties({JHipsterProperties.class})
public class GatewayServiceApplication {

	public static final Logger LOGGER = LoggerFactory.getLogger(GatewayServiceApplication.class);

	private Environment environment;

	public GatewayServiceApplication(Environment env){
		this.environment = env;
	}

	@PostConstruct
	public void initApplication() {
		Collection<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
		if (activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT) && activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_PRODUCTION)) {
			LOGGER.error("You have misconfigured your application! It should not run with both the 'dev' and 'prod' profiles at the same time.");
		}
	}

	public static void main(String[] args) {
		//SpringApplication.run(GatewayApplication.class, args);
		SpringApplication app = new SpringApplication(GatewayServiceApplication.class);
		DefaultProfileUtil.addDefaultProfile(app);
		Environment environment = app.run(args).getEnvironment();
		loadApplicationStartup(environment);

	}

	private static void loadApplicationStartup(Environment environment) {
		String protocol = Optional.ofNullable(environment.getProperty("server.ssl.key-store")).map(key -> "https").orElse("http");
		String serverPort = environment.getProperty("server.port");
		String contextPath = Optional
				.ofNullable(environment.getProperty("server.servlet.context-path"))
				.filter(StringUtils::isNotBlank)
				.orElse("/");
		String hostAddress = "localhost";
		try {
			hostAddress = InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			LOGGER.warn("The host name could not be determined, using `localhost` as fallback");
		}

		LOGGER.info(
				"\n----------------------------------------------------------\n\t" +
						"Application '{}' is running! Access URLs:\n\t" +
						"Local: \t\t{}://localhost:{}{}\n\t" +
						"External: \t{}://{}:{}{}\n\t" +
						"Profile(s): \t{}\n----------------------------------------------------------",
				environment.getProperty("spring.application.name"),
				protocol,
				serverPort,
				contextPath,
				protocol,
				hostAddress,
				serverPort,
				contextPath,
				environment.getActiveProfiles().length == 0 ? environment.getDefaultProfiles() : environment.getActiveProfiles()
		);
	}
}
