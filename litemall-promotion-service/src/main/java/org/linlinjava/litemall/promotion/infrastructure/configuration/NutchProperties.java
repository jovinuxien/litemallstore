package org.linlinjava.litemall.promotion.infrastructure.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Typed, environment-overridable settings for the Nutch crawl-ingest ACL
 * (Phase 3 crawl → read model). Nutch 1.8 itself runs outside this service and
 * indexes to Solr; this is an ingestion ACL only. Bound from
 * {@code litemall.promotion.nutch.*}.
 *
 * <p>Disabled by default; the Solr select URL and core name come via
 * {@code ${ENV:default}}. When {@code enabled=false} or the index is unreachable
 * the ingest adapter returns no signals (logged) — a campaign evaluation simply
 * proceeds without crawl enrichment.
 */
@Component
@ConfigurationProperties(prefix = "litemall.promotion.nutch")
@Getter
@Setter
public class NutchProperties {

    /** When false the Nutch ingest adapter is a no-op (returns no signals). */
    private boolean enabled = false;

    /** Solr base URL Nutch indexes to (e.g. {@code http://solr.example.com:8983/solr}); from env. */
    private String solrUrl;

    /** Solr core/collection holding the Nutch crawl documents. */
    private String core = "nutch";

    /** Default Solr query for the crawl read model ({@code q}). */
    private String query = "*:*";

    /** Max documents pulled per ingest ({@code rows}). */
    private int rows = 1000;
}
