package org.linlinjava.litemall.promotion.application.ports;

import org.linlinjava.litemall.promotion.domain.model.valueobjects.crawled.CrawledProductSignal;

import java.util.List;

/**
 * Port supplying market/product signals ingested from an external crawl (Phase 3
 * Nutch → read model). Nutch 1.8 runs outside this service and indexes to Solr;
 * the default implementation is a Solr-backed ingestion ACL
 * ({@code infrastructure/acl/nutch}). Callers depend only on this port.
 *
 * <p>Best-effort enrichment: when crawl ingestion is disabled or the index is
 * unreachable the implementation returns an empty list, and a campaign evaluation
 * proceeds without crawl signals. The crawl→ingest field contract is documented
 * in {@code docs/phase3-marketing-stack-integration.md}.
 */
public interface CrawledMarketDataProvider {

    /** Fetch up to the configured number of crawled product/market signals. */
    List<CrawledProductSignal> fetchSignals();
}
