package org.linlinjava.litemall.promotion.infrastructure.acl.nutch;

import org.linlinjava.litemall.promotion.infrastructure.acl.nutch.dto.NutchSolrResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client over the Solr index Nutch 1.8 writes its crawl to (Phase 3 crawl
 * → read model). Host from {@code litemall.promotion.nutch.solr-url}
 * (profile-overridable; no hardcoded host). Wrapped by
 * {@link NutchCrawlIngestAdapter} (ACL) so domain/application code depends only on
 * the {@code CrawledMarketDataProvider} port, never on Solr/Nutch wire types.
 */
@FeignClient(name = "nutch-index", url = "${litemall.promotion.nutch.solr-url:http://localhost}")
public interface NutchIndexClient {

    @GetMapping("/{core}/select?wt=json")
    NutchSolrResponse select(@PathVariable("core") String core,
                             @RequestParam("q") String query,
                             @RequestParam("rows") int rows);
}
