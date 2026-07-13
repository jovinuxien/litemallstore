package org.linlinjava.litemall.order.infrastructure.configuration;

import org.linlinjava.litemall.core.express.ExpressService;
import org.linlinjava.litemall.order.infrastructure.acl.express.CachingExpressQueryPort;
import org.linlinjava.litemall.order.infrastructure.acl.express.KdniaoExpressQueryAdapter;
import org.linlinjava.litemall.order.infrastructure.acl.express.NoopExpressQueryAdapter;
import org.linlinjava.litemall.order.infrastructure.acl.express.onepass.OnePassExpressQueryAdapter;
import org.linlinjava.litemall.order.infrastructure.acl.express.onepass.OnePassTokenClient;
import org.linlinjava.litemall.order.infrastructure.acl.printer.LoggingReceiptPrinterAdapter;
import org.linlinjava.litemall.order.infrastructure.acl.printer.yly.YlyOpenApiClient;
import org.linlinjava.litemall.order.infrastructure.acl.printer.yly.YlyReceiptPrinterAdapter;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.ExpressQueryPort;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.ReceiptPrinterPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.Locale;

/**
 * Provider selection for the Wave-4 fulfillment seams (the promotion-service
 * {@code StatsSourceConfiguration} pattern): ONE factory {@code @Bean} per port, switching
 * on {@code litemall.order.{printer,express}.provider}. The adapters are plain classes
 * constructed HERE (not {@code @Component}s) so exactly one bean of each port type exists —
 * no two-bean ambiguity, no {@code @Qualifier}s at injection points.
 *
 * <p>Misconfiguration fails FAST at startup ({@link IllegalStateException}): a deployment
 * that asks for {@code yly} without credentials, or {@code onepass} without account/secret,
 * should never boot into silently-broken printing/tracking.
 */
@Configuration
public class FulfillmentSeamsConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentSeamsConfiguration.class);

    @Bean
    @Primary
    public ReceiptPrinterPort receiptPrinterPort(FulfillmentProperties properties) {
        FulfillmentProperties.Printer printer = properties.getPrinter();
        String provider = normalize(printer.getProvider());
        log.info("Receipt printer provider = {}", provider);
        switch (provider) {
            case "yly":
                if (isBlank(printer.getClientId()) || isBlank(printer.getClientSecret())
                        || isBlank(printer.getMachineCode())) {
                    throw new IllegalStateException("litemall.order.printer.provider=yly requires "
                            + "client-id, client-secret and machine-code (supply via env vars, e.g. "
                            + "LITEMALL_ORDER_PRINTER_CLIENT_SECRET) — refusing to boot half-configured");
                }
                YlyOpenApiClient client = new YlyOpenApiClient(
                        printer.getBaseUrl(), printer.getClientId(), printer.getClientSecret(),
                        printer.getMachineCode(), printer.getMachineSecret(),
                        printer.getConnectTimeoutMs(), printer.getReadTimeoutMs());
                return new YlyReceiptPrinterAdapter(client);
            case "none":
            default:
                return new LoggingReceiptPrinterAdapter();
        }
    }

    /**
     * {@code expressService} arrives as an {@link ObjectProvider} so {@code provider=none}
     * (and {@code onepass}) boot even on a classpath/profile without core's
     * {@code ExpressAutoConfiguration}; only {@code kdniao} resolves the bean, and fails
     * fast if it is absent.
     */
    @Bean
    @Primary
    public ExpressQueryPort expressQueryPort(FulfillmentProperties properties,
                                             ObjectProvider<ExpressService> expressService) {
        FulfillmentProperties.Express express = properties.getExpress();
        String provider = normalize(express.getProvider());
        log.info("Express tracking provider = {}", provider);
        Duration ttl = Duration.ofMinutes(Math.max(1, express.getCacheMinutes()));
        switch (provider) {
            case "kdniao": {
                ExpressService service = expressService.getIfAvailable();
                if (service == null) {
                    throw new IllegalStateException("litemall.order.express.provider=kdniao but "
                            + "litemall-core's ExpressService bean is absent from this context");
                }
                return new CachingExpressQueryPort(new KdniaoExpressQueryAdapter(service), ttl);
            }
            case "onepass": {
                FulfillmentProperties.Express.Onepass onepass = express.getOnepass();
                if (isBlank(onepass.getAccount()) || isBlank(onepass.getSecret())) {
                    throw new IllegalStateException("litemall.order.express.provider=onepass requires "
                            + "onepass.account and onepass.secret (supply via env vars, e.g. "
                            + "LITEMALL_ORDER_EXPRESS_ONEPASS_SECRET) — refusing to boot half-configured");
                }
                OnePassTokenClient tokenClient = new OnePassTokenClient(
                        onepass.getBaseUrl(), onepass.getAccount(), onepass.getSecret(),
                        express.getConnectTimeoutMs(), express.getReadTimeoutMs());
                return new CachingExpressQueryPort(new OnePassExpressQueryAdapter(
                        tokenClient, onepass.getBaseUrl(),
                        express.getConnectTimeoutMs(), express.getReadTimeoutMs()), ttl);
            }
            case "none":
            default:
                return new NoopExpressQueryAdapter(); // uncached — nothing to cache
        }
    }

    private static String normalize(String provider) {
        return provider == null ? "none" : provider.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
