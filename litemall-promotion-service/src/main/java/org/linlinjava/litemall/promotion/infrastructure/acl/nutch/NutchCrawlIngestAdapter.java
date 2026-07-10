package org.linlinjava.litemall.promotion.infrastructure.acl.nutch;

import org.linlinjava.litemall.promotion.application.ports.CrawledMarketDataProvider;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.crawled.CrawledProductSignal;
import org.linlinjava.litemall.promotion.infrastructure.acl.nutch.dto.NutchSolrResponse;
import org.linlinjava.litemall.promotion.infrastructure.configuration.NutchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Anti-corruption layer over {@link NutchIndexClient}: ingests Nutch crawl
 * documents from Solr and maps them to domain {@link CrawledProductSignal} value
 * objects, so the targeting engine never sees a Solr/Nutch DTO. The
 * {@link CrawledMarketDataProvider} implementation.
 *
 * <p>Field mapping (documented in {@code docs/phase3-marketing-stack-integration.md}):
 * Nutch/Solr {@code url}→sourceUrl, {@code host}→host, {@code title}→title,
 * {@code content}→snippet (truncated). {@code crawledPrice} is left null —
 * price extraction is future enrichment.
 *
 * <p>Degrades gracefully — disabled, a transport failure, or a malformed payload
 * yields no signals (logged), so campaign evaluation proceeds without crawl data.
 */
@Component
public class NutchCrawlIngestAdapter implements CrawledMarketDataProvider {

    private static final Logger logger = LoggerFactory.getLogger(NutchCrawlIngestAdapter.class);

    private static final int SNIPPET_MAX = 280;

    private final NutchIndexClient nutchIndexClient;
    private final NutchProperties properties;

    public NutchCrawlIngestAdapter(NutchIndexClient nutchIndexClient, NutchProperties properties) {
        this.nutchIndexClient = nutchIndexClient;
        this.properties = properties;
    }

    @Override
    public List<CrawledProductSignal> fetchSignals() {
        if (!properties.isEnabled()) {
            logger.debug("Nutch crawl ingestion disabled; returning no signals");
            return new ArrayList<>();
        }
        try {
            NutchSolrResponse response = nutchIndexClient.select(
                    properties.getCore(), properties.getQuery(), properties.getRows());
            if (response == null || response.getResponse() == null
                    || response.getResponse().getDocs() == null) {
                logger.warn("Nutch crawl ingest empty (core={}, q={})",
                        properties.getCore(), properties.getQuery());
                return new ArrayList<>();
            }
            List<CrawledProductSignal> signals = new ArrayList<>();
            for (Map<String, Object> doc : response.getResponse().getDocs()) {
                CrawledProductSignal signal = toDomain(doc);
                if (signal != null) {
                    signals.add(signal);
                }
            }
            logger.info("Nutch crawl ingest: {} signals (core={})", signals.size(), properties.getCore());
            return signals;
        } catch (Exception e) {
            logger.error("Failed to ingest Nutch crawl (core={}): {}",
                    properties.getCore(), e.getMessage());
            return new ArrayList<>();
        }
    }

    private CrawledProductSignal toDomain(Map<String, Object> doc) {
        if (doc == null) {
            return null;
        }
        String url = asString(doc.get("url"));
        if (url == null || url.isBlank()) {
            return null;
        }
        String host = asString(doc.get("host"));
        String title = asString(doc.get("title"));
        String snippet = truncate(asString(doc.get("content")));
        BigDecimal crawledPrice = null; // future enrichment — see runbook
        return new CrawledProductSignal(url, host, title, snippet, crawledPrice);
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List) {
            // Solr multi-valued fields come back as arrays — take the first entry.
            List<?> list = (List<?>) value;
            return list.isEmpty() ? null : String.valueOf(list.get(0));
        }
        return String.valueOf(value);
    }

    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= SNIPPET_MAX ? text : text.substring(0, SNIPPET_MAX);
    }
}
