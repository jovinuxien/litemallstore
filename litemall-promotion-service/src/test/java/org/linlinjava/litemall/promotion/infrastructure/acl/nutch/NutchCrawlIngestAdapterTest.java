package org.linlinjava.litemall.promotion.infrastructure.acl.nutch;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.crawled.CrawledProductSignal;
import org.linlinjava.litemall.promotion.infrastructure.acl.nutch.dto.NutchSolrResponse;
import org.linlinjava.litemall.promotion.infrastructure.configuration.NutchProperties;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for the Nutch ACL — maps Solr crawl docs to {@link
 * CrawledProductSignal} (incl. Solr multi-valued fields), and degrades to no
 * signals when disabled or the client throws. Uses hand-rolled fakes (no Mockito).
 */
class NutchCrawlIngestAdapterTest {

    private NutchProperties enabledProps() {
        NutchProperties p = new NutchProperties();
        p.setEnabled(true);
        return p;
    }

    private NutchSolrResponse responseWith(List<Map<String, Object>> docs) {
        NutchSolrResponse r = new NutchSolrResponse();
        NutchSolrResponse.ResponseBody body = new NutchSolrResponse.ResponseBody();
        body.setDocs(docs);
        r.setResponse(body);
        return r;
    }

    private Map<String, Object> doc(Object url, Object title, Object content, Object host) {
        Map<String, Object> m = new HashMap<>();
        m.put("url", url);
        m.put("title", title);
        m.put("content", content);
        m.put("host", host);
        return m;
    }

    @Test
    void mapsSolrDocsToSignals() {
        NutchSolrResponse response = responseWith(Arrays.asList(
                doc("http://shop.example.com/p/1", List.of("Widget"), "A nice widget", "shop.example.com"),
                doc(null, "no url", "dropped", "x.com")));   // missing url → skipped

        NutchIndexClient client = (core, query, rows) -> response;
        NutchCrawlIngestAdapter adapter = new NutchCrawlIngestAdapter(client, enabledProps());

        List<CrawledProductSignal> signals = adapter.fetchSignals();

        assertEquals(1, signals.size(), "doc without url is dropped");
        CrawledProductSignal s = signals.get(0);
        assertEquals("http://shop.example.com/p/1", s.getSourceUrl());
        assertEquals("Widget", s.getTitle(), "Solr multi-valued field → first element");
        assertEquals("A nice widget", s.getSnippet());
        assertEquals("shop.example.com", s.getHost());
    }

    @Test
    void returnsEmptyWhenDisabled() {
        NutchIndexClient client = (core, query, rows) -> {
            throw new AssertionError("client must not be called when disabled");
        };
        NutchCrawlIngestAdapter adapter = new NutchCrawlIngestAdapter(client, new NutchProperties());
        assertTrue(adapter.fetchSignals().isEmpty());
    }

    @Test
    void degradesToEmptyOnClientFailure() {
        NutchIndexClient client = (core, query, rows) -> {
            throw new RuntimeException("solr down");
        };
        NutchCrawlIngestAdapter adapter = new NutchCrawlIngestAdapter(client, enabledProps());
        assertTrue(adapter.fetchSignals().isEmpty());
    }
}
