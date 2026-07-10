package org.linlinjava.litemall.promotion.domain.model.valueobjects.crawled;

import lombok.Getter;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A single market/product signal ingested from a Nutch crawl (Phase 3 crawl →
 * read model). A domain value object so the targeting engine never depends on a
 * Solr/Nutch DTO. Sourced behind the {@code CrawledMarketDataProvider} port.
 *
 * <p>{@code crawledPrice} is nullable — price extraction from crawled content is
 * best-effort and documented as future enrichment in the Phase-3 runbook; the
 * core fields ({@code sourceUrl}, {@code title}) always come straight from the
 * crawl document.
 */
@Getter
public class CrawledProductSignal {

    private final String sourceUrl;
    private final String host;
    private final String title;
    private final String snippet;
    private final BigDecimal crawledPrice;

    public CrawledProductSignal(String sourceUrl, String host, String title,
                                String snippet, BigDecimal crawledPrice) {
        this.sourceUrl = sourceUrl;
        this.host = host;
        this.title = title;
        this.snippet = snippet;
        this.crawledPrice = crawledPrice;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CrawledProductSignal that = (CrawledProductSignal) o;
        return Objects.equals(sourceUrl, that.sourceUrl) && Objects.equals(title, that.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceUrl, title);
    }
}
