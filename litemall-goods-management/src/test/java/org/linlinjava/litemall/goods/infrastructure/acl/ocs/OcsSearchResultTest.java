package org.linlinjava.litemall.goods.infrastructure.acl.ocs;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Jackson binding of the OCS SearchResult envelope — in particular the Wave-9 permissive
 * {@code highlight}/{@code metaData} hit fields (absent from the deployed searcher today, bound
 * for forward compatibility) and unknown-field tolerance.
 */
public class OcsSearchResultTest {

    @Test
    public void bindsHighlightMetaDataAndToleratesUnknownFields() throws Exception {
        OcsSearchResult result;
        try (InputStream in = getClass().getResourceAsStream("/ocs/search-result-with-highlight.json")) {
            result = new ObjectMapper().readValue(in, OcsSearchResult.class);
        }

        assertThat(result.totalMatchCount()).isEqualTo(2);
        assertThat(result.getMeta()).containsEntry("query_strategy", "default-query");
        assertThat(result.getSortOptions()).hasSize(1);

        OcsSearchResult.Hit first = result.getSlices().get(0).getHits().get(0);
        assertThat(first.getDocument().getId()).isEqualTo("1152161");
        assertThat(first.getDocument().getData()).containsEntry("title", "Ice Silk Boxer Briefs");
        assertThat(first.getHighlight()).containsEntry("title", "Ice <em>Silk</em> Boxer Briefs");
        assertThat(first.getMetaData()).containsKey("score");

        // A hit without highlight/metaData (today's live shape) binds to nulls, not errors.
        OcsSearchResult.Hit second = result.getSlices().get(0).getHits().get(1);
        assertThat(second.getHighlight()).isNull();
        assertThat(second.getMetaData()).isNull();
    }
}
