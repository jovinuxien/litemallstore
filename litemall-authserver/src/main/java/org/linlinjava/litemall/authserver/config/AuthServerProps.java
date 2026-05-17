package org.linlinjava.litemall.authserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Client-credentials secrets for the two edge gateways.
 *
 * <p>Bound from {@code litemall.authserver.*}. Dev defaults are plaintext (a
 * {@code NoOpPasswordEncoder} is used) — supply real secrets via config-server
 * / environment for any non-dev use.
 */
@ConfigurationProperties(prefix = "litemall.authserver")
public class AuthServerProps {

    /** client_credentials secret for the admin edge (client_id "gateway-admin"). */
    private String gatewayAdminSecret = "gateway-admin-dev-secret";

    /** client_credentials secret for the customer edge (client_id "gateway-api"). */
    private String gatewayApiSecret = "gateway-api-dev-secret";

    public String getGatewayAdminSecret() {
        return gatewayAdminSecret;
    }

    public void setGatewayAdminSecret(String gatewayAdminSecret) {
        this.gatewayAdminSecret = gatewayAdminSecret;
    }

    public String getGatewayApiSecret() {
        return gatewayApiSecret;
    }

    public void setGatewayApiSecret(String gatewayApiSecret) {
        this.gatewayApiSecret = gatewayApiSecret;
    }
}
