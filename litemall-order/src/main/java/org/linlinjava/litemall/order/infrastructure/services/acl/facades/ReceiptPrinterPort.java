package org.linlinjava.litemall.order.infrastructure.services.acl.facades;

import org.linlinjava.litemall.order.infrastructure.services.acl.facades.fulfillment.ReceiptPrintJob;

/**
 * Receipt-printer seam (Wave 4, Task D — docs/adr-fulfillment-seams.md). The application
 * layer prints through this port only; the active adapter (logging no-op vs Yly cloud
 * printer) is chosen by {@code litemall.order.printer.provider} in
 * {@code FulfillmentSeamsConfiguration}.
 *
 * <p>Contract: {@link #print(ReceiptPrintJob)} NEVER throws — adapters swallow transport,
 * auth and parse failures into {@link PrintOutcome#FAILED}. Printing is strictly
 * best-effort; a paid order must never block or fail on a printer.
 */
public interface ReceiptPrinterPort {

    /** Best-effort print. Never throws; always safe to call, even when {@link #enabled()} is false. */
    PrintOutcome print(ReceiptPrintJob job);

    /**
     * Whether a REAL printer provider is configured. Gates ONLY the admin reprint surface
     * (errno 641): the logging adapter returns {@code false} here but its {@code print()}
     * still renders + logs — that is the disabled-mode verification path for the receipt
     * template (auto-print keeps calling {@code print()} regardless).
     */
    boolean enabled();

    enum PrintOutcome {
        /** The provider accepted the job (or the logging adapter rendered it). */
        OK,
        /** The provider rejected the job or was unreachable; already logged by the adapter. */
        FAILED
    }
}
