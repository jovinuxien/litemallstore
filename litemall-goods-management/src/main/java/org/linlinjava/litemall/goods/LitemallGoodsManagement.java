package org.linlinjava.litemall.goods;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.core.env.Environment;
import tech.jhipster.config.DefaultProfileUtil;
import tech.jhipster.config.JHipsterConstants;

import javax.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;


@SpringBootApplication(scanBasePackages = {"org.linlinjava.litemall.db", "org.linlinjava.litemall.db.dao", "org.linlinjava.litemall.core", "org.linlinjava.litemall.goods"})
@EnableEurekaClient
//@EnableCircuitBreaker
//@EnableBinding(Source.class) //This way of binding is deprecated
public class LitemallGoodsManagement {

	public static final Logger LOGGER = LoggerFactory.getLogger(LitemallGoodsManagement.class);

	private Environment environment;

	public LitemallGoodsManagement(Environment env){
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
		SpringApplication app = new SpringApplication(LitemallGoodsManagement.class);
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
