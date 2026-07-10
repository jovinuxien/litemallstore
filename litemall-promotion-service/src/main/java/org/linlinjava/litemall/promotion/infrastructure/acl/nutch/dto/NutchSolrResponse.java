package org.linlinjava.litemall.promotion.infrastructure.acl.nutch.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * Wire shape of a Solr {@code /select} response holding Nutch crawl documents —
 * the docs live under {@code response.docs}. Each doc is kept as a loose map
 * (Nutch/Solr schemas vary by crawl config); {@code NutchCrawlIngestAdapter}
 * pulls the agreed fields out. {@code @JsonIgnoreProperties} tolerates the
 * responseHeader and other extras.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NutchSolrResponse {

    private ResponseBody response;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResponseBody {
        private long numFound;
        private List<Map<String, Object>> docs;
    }
}
