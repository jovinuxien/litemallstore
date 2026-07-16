package org.linlinjava.litemall.core.mail.config;

import org.linlinjava.litemall.core.mail.CustomerMailProperties;
import org.linlinjava.litemall.core.mail.CustomerMailSender;
import org.linlinjava.litemall.core.mail.LoggingCustomerMailSender;
import org.linlinjava.litemall.core.mail.SmtpCustomerMailSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds the single {@link CustomerMailSender} bean (Wave 6). Mirrors
 * {@link org.linlinjava.litemall.core.notify.config.NotifyAutoConfiguration}:
 * picked up by every service that component-scans {@code org.linlinjava.litemall.core}.
 *
 * <p>Disabled (the default) → {@link LoggingCustomerMailSender}, so dependents boot
 * byte-identical with no SMTP anywhere. {@code @ConditionalOnMissingBean} lets a
 * service supply its own sender (e.g. a test double) without a bean clash.
 */
@Configuration
@EnableConfigurationProperties(CustomerMailProperties.class)
public class CustomerMailAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CustomerMailSender.class)
    public CustomerMailSender customerMailSender(CustomerMailProperties properties) {
        if (properties.isEnabled()) {
            return new SmtpCustomerMailSender(properties);
        }
        return new LoggingCustomerMailSender();
    }
}
