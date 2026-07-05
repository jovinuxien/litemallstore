package org.linlinjava.litemall.order.infrastructure.services.acl.facades;

import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.dispute.CjDisputableLine;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.dispute.CjDisputeOpenCommand;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.dispute.CjDisputeQuote;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.dispute.CjDisputeSnapshot;

import java.util.List;

/**
 * Anticorruption layer over the CJ Dropshipping dispute API (Millett/Tune ch. 7 context
 * mapping): the application layer speaks these domain-language operations and types;
 * CJ's wire jargon ({@code expectType}/{@code finallyDeal} integers, {@code lineItemId},
 * boolean-data envelopes) never crosses this boundary. Every failure — transport,
 * breaker-open, or CJ business rejection — surfaces as
 * {@code LitemallCjDisputeException} with CJ's message.
 */
public interface CjDisputeFacade {

    /** The CJ order's lines that may be disputed (joinable to our rows via cjVariantId). */
    List<CjDisputableLine> disputableLines(String cjOrderId);

    /** What a dispute over the given lines may claim + the selectable reasons. */
    CjDisputeQuote quote(String cjOrderId, List<CjDisputeOpenCommand.Line> lines);

    /**
     * Open the dispute at CJ. Returns nothing: CJ's create only acknowledges; the CJ
     * dispute id is reconciled later from {@link #fetchDisputes} (eventual consistency
     * across the context boundary — our businessDisputeId is the retry-safe key).
     */
    void open(CjDisputeOpenCommand command);

    /** Cancel a dispute CJ has registered (requires CJ's dispute id). */
    void cancel(String cjOrderId, String cjDisputeId);

    /** CJ's current disputes for the order, newest first as CJ returns them. */
    List<CjDisputeSnapshot> fetchDisputes(String cjOrderId);
}
