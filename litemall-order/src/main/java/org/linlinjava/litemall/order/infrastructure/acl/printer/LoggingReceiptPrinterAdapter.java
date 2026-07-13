package org.linlinjava.litemall.order.infrastructure.acl.printer;

import org.linlinjava.litemall.order.infrastructure.services.acl.facades.ReceiptPrinterPort;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.fulfillment.ReceiptPrintJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default (provider {@code none}) printer adapter: renders the full receipt through the
 * SHARED {@code ReceiptRenderer} template and logs it, so the receipt content is
 * verifiable in dev without any Yly account. {@link #enabled()} is {@code false} — the
 * admin reprint surface answers errno 641 — but {@link #print} still renders + returns
 * {@code OK}: that IS the disabled-mode verification path (auto-print exercises it on
 * every payment).
 *
 * <p>Plain class, constructed by {@code FulfillmentSeamsConfiguration} (not a
 * {@code @Component}) so only one {@code ReceiptPrinterPort} bean ever exists.
 */
public class LoggingReceiptPrinterAdapter implements ReceiptPrinterPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingReceiptPrinterAdapter.class);

    @Override
    public PrintOutcome print(ReceiptPrintJob job) {
        // One statement so the multi-line receipt stays a single log record.
        log.info("[receipt] originId={} (printer provider: none — logging only)\n{}",
                job.getOriginId(), ReceiptRenderer.render(job));
        return PrintOutcome.OK;
    }

    @Override
    public boolean enabled() {
        return false;
    }
}
