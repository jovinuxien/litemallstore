package org.linlinjava.litemall.order;

//import org.mybatis.spring.annotation.MapperScan;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.core.env.Environment;
import tech.jhipster.config.DefaultProfileUtil;
import tech.jhipster.config.JHipsterConstants;

import javax.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

@SpringBootApplication(scanBasePackages = {"org.linlinjava.litemall.db", "org.linlinjava.litemall.core"})
//@MapperScan("org.linlinjava.litemall.db.dao")
@EnableEurekaClient
@EnableFeignClients
//@EnableCircuitBreaker
public class LitemallOrderServiceApplication {


	public static final Logger LOGGER = LoggerFactory.getLogger(LitemallOrderServiceApplication.class);

	private Environment environment;

	public LitemallOrderServiceApplication(Environment env){
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
		SpringApplication app = new SpringApplication(LitemallOrderServiceApplication.class);
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
						"Order Service application '{}' is running! Access URLs:\n\t" +
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
