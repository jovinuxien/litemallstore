package org.linlinjava.litemall.goods.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * OCS endpoint configuration. Hosts are NOT defaulted in Java — they are bound
 * from {@code litemall.search.*} in {@code config/application.yml} (localhost
 * stack) and overridden by {@code application-docker.yml} (compose service
 * names) or the {@code litemall-config} repo per environment. Keeping the host
 * fields undefaulted guarantees there is no hardcoded {@code localhost}/port in
 * the codebase and that every endpoint stays profile-overridable.
 */
@ConfigurationProperties(prefix = "litemall.search")
public class LitemallSearchProperties {

    private String indexerUrl;
    private String searchUrl;
    private String suggestUrl;
    private String indexName = "litemall_index";
    private String locale = "en";

    /**
     * Curated allow-list of {@code litemall_goods_attribute} names indexed as facetable attributes.
     * Only these become document data keys (and so facets, via the index config's {@code dynamic-fields}),
     * keeping descriptive/high-cardinality attributes (e.g. "Kind tips") out of the facet rail.
     * Matched case-insensitively. Override per environment via {@code litemall.search.facet-attributes}.
     */
    private List<String> facetAttributes = List.of(
            "Material", "color", "Origin", "Fabric", "filler", "weight");

    public String getIndexerUrl() {
        return indexerUrl;
    }

    public void setIndexerUrl(String indexerUrl) {
        this.indexerUrl = indexerUrl;
    }

    public String getSearchUrl() {
        return searchUrl;
    }

    public void setSearchUrl(String searchUrl) {
        this.searchUrl = searchUrl;
    }

    public String getSuggestUrl() {
        return suggestUrl;
    }

    public void setSuggestUrl(String suggestUrl) {
        this.suggestUrl = suggestUrl;
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public List<String> getFacetAttributes() {
        return facetAttributes;
    }

    public void setFacetAttributes(List<String> facetAttributes) {
        this.facetAttributes = facetAttributes;
    }
}
