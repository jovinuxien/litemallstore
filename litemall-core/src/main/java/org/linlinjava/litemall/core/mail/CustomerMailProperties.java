package org.linlinjava.litemall.core.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code litemall.customer-mail.*} — the SHARED customer-mail config namespace
 * (Wave 6). The same keys drive core's {@link SmtpCustomerMailSender} (order's
 * outbox sweep) and gateway-api's edge-local reset-mail sender.
 *
 * <p>ENABLE VIA ENV VARS (e.g. {@code LITEMALL_CUSTOMERMAIL_ENABLED=true}):
 * litemall-core's profile yml outranks every service yml (the kdniao precedence
 * lesson), so per-service yml overrides of these keys silently lose. Defaults
 * point at MailHog on localhost:1025 for dev.
 */
@ConfigurationProperties(prefix = "litemall.customer-mail")
public class CustomerMailProperties {

    /** Master switch; false = no-op logging sender, byte-identical boot. */
    private boolean enabled = false;
    /** From address stamped on every customer mail. */
    private String from = "noreply@trovemo.com";
    /** SMTP host (dev default = MailHog). */
    private String host = "localhost";
    /** SMTP port (dev default = MailHog's 1025). */
    private int port = 1025;
    /** SMTP username; blank = no SMTP auth (MailHog needs none). */
    private String username = "";
    private String password = "";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
