package org.linlinjava.litemall.order.infrastructure.acl.printer.yly;

import org.linlinjava.litemall.order.infrastructure.acl.printer.ReceiptRenderer;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.ReceiptPrinterPort;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.fulfillment.ReceiptPrintJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ReceiptPrinterPort} over the Yly cloud printer (provider {@code yly}). Renders
 * through the SAME {@code ReceiptRenderer} template as the logging adapter — Yly accepts
 * plain text (its markup tags like {@code <FS>} are optional), so no extra wrapping is
 * needed. Never throws: any client-level failure is already logged and comes back FAILED.
 *
 * <p>Plain class, constructed (and its config validated fail-fast) by
 * {@code FulfillmentSeamsConfiguration}.
 */
public class YlyReceiptPrinterAdapter implements ReceiptPrinterPort {

    private static final Logger log = LoggerFactory.getLogger(YlyReceiptPrinterAdapter.class);

    private final YlyOpenApiClient client;

    public YlyReceiptPrinterAdapter(YlyOpenApiClient client) {
        this.client = client;
    }

    @Override
    public PrintOutcome print(ReceiptPrintJob job) {
        try {
            String content = ReceiptRenderer.render(job);
            return client.print(job.getOriginId(), content) ? PrintOutcome.OK : PrintOutcome.FAILED;
        } catch (RuntimeException e) {
            // Belt and braces: the port contract is "never throws".
            log.warn("Yly print failed for originId {}: {}", job.getOriginId(), e.getMessage());
            return PrintOutcome.FAILED;
        }
    }

    @Override
    public boolean enabled() {
        return true;
    }
}
