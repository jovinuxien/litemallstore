package org.linlinjava.litemall.goods.application.search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 26: the guard that makes a RAISED enrich batch size safe to run unattended.
 *
 * <p>CJ's daily API points are shared ACCOUNT-WIDE with ORDER PLACEMENT (catalog and order use
 * different key pairs on the same CJ account — verified on prod 2026-08-14). Before this, a
 * failing batch logged each row and kept going, so a 400-product batch would fire hundreds of
 * futile calls against an exhausted quota — burning the budget paid orders need, and buying
 * nothing, since every remaining row fails too.
 *
 * <p>CJ signals exhaustion as free text inside a generic RuntimeException, so detection is
 * message-based and therefore fragile; the consecutive-failure abort is the backstop that does
 * not depend on CJ's wording at all.
 */
public class EnrichBatchAbortTest {

    @Test
    public void recognisesCjQuotaExhaustion() {
        assertTrue(CjDetailEnrichmentService.looksLikeQuotaExhaustion(
                "Product detail fetch failed: {\"code\":16900500,\"message\":\"api points not enough\"}"));
        assertTrue(CjDetailEnrichmentService.looksLikeQuotaExhaustion("API POINTS exhausted"));
        assertTrue(CjDetailEnrichmentService.looksLikeQuotaExhaustion("daily Quota reached"));
    }

    @Test
    public void doesNotMistakeOrdinaryFailuresForExhaustion() {
        assertFalse(CjDetailEnrichmentService.looksLikeQuotaExhaustion(
                "Product detail fetch failed: QPS limit is 1 time/1second"));
        assertFalse(CjDetailEnrichmentService.looksLikeQuotaExhaustion("connection reset"));
        assertFalse(CjDetailEnrichmentService.looksLikeQuotaExhaustion(null));
        assertFalse(CjDetailEnrichmentService.looksLikeQuotaExhaustion(""));
    }

    /**
     * A QPS rejection is transient and must NOT look like exhaustion — otherwise the backstop is
     * the only thing stopping a batch, and it would abandon runs that would have recovered.
     */
    @Test
    public void theConsecutiveFailureBackstopIsSmallEnoughToMatter() {
        assertTrue(CjDetailEnrichmentService.CONSECUTIVE_FAILURE_ABORT > 0);
        assertTrue(CjDetailEnrichmentService.CONSECUTIVE_FAILURE_ABORT <= 10,
                "a backstop larger than a handful defeats the purpose at batch sizes in the hundreds");
    }

    /**
     * A product CJ has no detail for is a DATA GAP, not a fault, and must not count toward the
     * abandon counter. Prod 2026-08-15 proved why: a 400-product batch stopped after 119 enriched
     * because five consecutive rows were plain data gaps, so raising the batch from 100 bought
     * ~19 extra products instead of ~300. The guard was protecting nothing and cost most of the run.
     */
    @Test
    public void aMissingCjDetailIsADataGapNotAFault() {
        assertTrue(CjDetailEnrichmentService.isDataGap(
                "no CJ detail for pid 2608141057281625900"));
        assertTrue(CjDetailEnrichmentService.isDataGap(
                "No CJ Detail For Pid 123"));
    }

    @Test
    public void realFailuresAreStillFaults() {
        assertFalse(CjDetailEnrichmentService.isDataGap(
                "Product detail fetch failed: QPS limit is 1 time/1second"));
        assertFalse(CjDetailEnrichmentService.isDataGap("connection reset"));
        assertFalse(CjDetailEnrichmentService.isDataGap(null));
    }

    /** A data gap must not be mistaken for exhaustion either — different signals, different action. */
    @Test
    public void aDataGapIsNotQuotaExhaustion() {
        String msg = "no CJ detail for pid 2608141057281625900";
        assertTrue(CjDetailEnrichmentService.isDataGap(msg));
        assertFalse(CjDetailEnrichmentService.looksLikeQuotaExhaustion(msg));
    }
}
