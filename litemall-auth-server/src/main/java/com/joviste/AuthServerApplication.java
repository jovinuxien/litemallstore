package com.joviste;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.authentication.configuration.EnableGlobalAuthentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
//@EnableDiscoveryClient
public class AuthServerApplication {

	private static final Logger LOGGER = LoggerFactory.getLogger(AuthServerApplication.class);

	public static void main(String[] args) throws Exception {
		SpringApplication.run(AuthServerApplication.class, args);
	}

	@RequestMapping(
			value = "/",
			produces = MediaType.APPLICATION_JSON_VALUE
	)
	public ResponseEntity<String> index() {
		return new ResponseEntity<>("{\"message\":\"Home!\"}", HttpStatus.OK);
	}

}
