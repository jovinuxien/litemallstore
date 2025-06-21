package com.joviste.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "application", ignoreInvalidFields = false)
public class ApplicationProperties {
}
