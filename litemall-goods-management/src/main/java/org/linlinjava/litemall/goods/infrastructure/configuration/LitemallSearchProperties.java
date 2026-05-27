package org.linlinjava.litemall.goods.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OCS (Open Commerce Search) stack endpoints. Profile-split per CLAUDE.md:
 * docker service names for the container profile (e.g. http://indexer:8535)
 * and localhost for local dev. Overridable via the litemall-config repo.
 */
@ConfigurationProperties(prefix = "litemall.search")
public class LitemallSearchProperties {

    private String indexerUrl = "http://localhost:8535";
    private String searchUrl = "http://localhost:8534";
    private String suggestUrl = "http://localhost:8081";
    private String indexName = "litemall_index";

    public String getIndexerUrl() { return indexerUrl; }
    public void setIndexerUrl(String indexerUrl) { this.indexerUrl = indexerUrl; }

    public String getSearchUrl() { return searchUrl; }
    public void setSearchUrl(String searchUrl) { this.searchUrl = searchUrl; }

    public String getSuggestUrl() { return suggestUrl; }
    public void setSuggestUrl(String suggestUrl) { this.suggestUrl = suggestUrl; }

    public String getIndexName() { return indexName; }
    public void setIndexName(String indexName) { this.indexName = indexName; }
}
