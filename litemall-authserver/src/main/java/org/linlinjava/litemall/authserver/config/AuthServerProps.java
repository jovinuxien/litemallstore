package org.linlinjava.litemall.authserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Client-credentials secrets for the two edge gateways.
 *
 * <p>Bound from {@code litemall.authserver.*}. These are plaintext by design — a
 * {@code NoOpPasswordEncoder} is used — so they must come from the environment,
 * never from source.
 *
 * <p>Wave 7: literal dev defaults were hardcoded here and are in git history.
 * Anyone with repo access could mint a machine token for either edge. They are
 * gone (and should be considered burned — do not reuse them);
 * both are now empty by default and supplied via
 * {@code LITEMALL_AUTHSERVER_GATEWAY_*_SECRET}. Empty is the safe default: a
 * blank secret authenticates nothing, whereas a known one authenticates
 * everyone. {@code JwtKeyGuard} fails startup in prod if they are still blank.
 */
@ConfigurationProperties(prefix = "litemall.authserver")
public class AuthServerProps {

    /** client_credentials secret for the admin edge (client_id "gateway-admin"). */
    private String gatewayAdminSecret = "";

    /** client_credentials secret for the customer edge (client_id "gateway-api"). */
    private String gatewayApiSecret = "";

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
