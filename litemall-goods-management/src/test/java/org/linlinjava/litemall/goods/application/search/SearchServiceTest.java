package org.linlinjava.litemall.goods.application.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.goods.application.comment.CommentStatsService;
import org.linlinjava.litemall.goods.infrastructure.acl.ocs.OcsSearchClient;
import org.linlinjava.litemall.goods.infrastructure.acl.ocs.OcsSearchResult;
import org.linlinjava.litemall.goods.infrastructure.acl.ocs.OcsSuggestClient;
import org.linlinjava.litemall.goods.infrastructure.acl.ocs.OcsSuggestion;
import org.mockito.Mockito;
import org.springframework.web.client.ResourceAccessException;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

public class SearchServiceTest {

    private OcsSearchClient searchClient;
    private OcsSuggestClient suggestClient;
    private CommentStatsService commentStatsService;
    private SearchHighlighter searchHighlighter;
    private CategoryNameResolver categoryNameResolver;
    private SearchKeywordService searchKeywordService;
    private SearchService service;

    @BeforeEach
    void setup() {
        searchClient = Mockito.mock(OcsSearchClient.class);
        suggestClient = Mockito.mock(OcsSuggestClient.class);
        commentStatsService = Mockito.mock(CommentStatsService.class);
        searchHighlighter = new SearchHighlighter();
        categoryNameResolver = Mockito.mock(CategoryNameResolver.class);
        searchKeywordService = Mockito.mock(SearchKeywordService.class);
        service = new SearchService(searchClient, suggestClient, commentStatsService,
                searchHighlighter, categoryNameResolver, searchKeywordService);
        Mockito.when(searchKeywordService.helper(anyString(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());
    }

    // ---- helpers ----------------------------------------------------------

    private static OcsSearchResult resultWithHits(OcsSearchResult.Hit... hits) {
        OcsSearchResult result = new OcsSearchResult();
        OcsSearchResult.Slice slice = new OcsSearchResult.Slice();
        slice.setMatchCount(hits.length);
        slice.setHits(Arrays.asList(hits));
        result.setSlices(List.of(slice));
        return result;
    }

    private static OcsSearchResult.Hit hit(String id, String title, String description) {
        OcsSearchResult.Hit hit = new OcsSearchResult.Hit();
        OcsSearchResult.Document document = new OcsSearchResult.Document();
        document.setId(id);
        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("description", description);
        document.setData(data);
        hit.setDocument(document);
        return hit;
    }

    private static OcsSuggestion suggestion(String phrase, String type) {
        OcsSuggestion s = new OcsSuggestion();
        s.setPhrase(phrase);
        s.setType(type);
        return s;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> goodsList(Map<String, Object> response) {
        return (List<Map<String, Object>>) response.get("goodsList");
    }

    // ---- Wave-19 coupon_flag passthrough ----------------------------------

    @Test
    public void couponFlagRidesTheHitOnlyWhenPositive() {
        OcsSearchResult.Hit flagged = hit("1", "Silk Scarf", "desc");
        flagged.getDocument().getData().put("coupon_flag", 1);
        OcsSearchResult.Hit unflagged = hit("2", "Cotton Socks", "desc");
        unflagged.getDocument().getData().put("coupon_flag", 0);
        OcsSearchResult.Hit preReindex = hit("3", "Wool Hat", "desc"); // field absent

        Mockito.when(searchClient.search(anyString(), anyInt(), anyInt(), any(), any()))
                .thenReturn(resultWithHits(flagged, unflagged, preReindex));

        List<Map<String, Object>> items = goodsList(service.search("q", 1, 20, null, Map.of()));
        assertThat(items.get(0)).containsEntry("coupon_flag", 1);
        assertThat(items.get(1)).doesNotContainKey("coupon_flag");
        assertThat(items.get(2)).doesNotContainKey("coupon_flag");
    }

    // ---- Wave-27 eu_flag passthrough --------------------------------------

    @Test
    public void euFlagRidesTheHitOnlyWhenPositive() {
        OcsSearchResult.Hit flagged = hit("1", "Garden Hose Reel", "desc");
        flagged.getDocument().getData().put("eu_flag", 1);
        OcsSearchResult.Hit unflagged = hit("2", "Hand Plane", "desc");
        unflagged.getDocument().getData().put("eu_flag", 0);
        OcsSearchResult.Hit preReindex = hit("3", "Tool Chest", "desc"); // field absent

        Mockito.when(searchClient.search(anyString(), anyInt(), anyInt(), any(), any()))
                .thenReturn(resultWithHits(flagged, unflagged, preReindex));

        List<Map<String, Object>> items = goodsList(service.search("q", 1, 20, null, Map.of()));
        assertThat(items.get(0)).containsEntry("eu_flag", 1);
        // A product whose probe found no EU stock and a product indexed before eu_flag existed are
        // indistinguishable here, and both must render no badge — absence is not a negative claim.
        assertThat(items.get(1)).doesNotContainKey("eu_flag");
        assertThat(items.get(2)).doesNotContainKey("eu_flag");
    }

    // ---- seasons passthrough ----------------------------------------------

    @Test
    public void seasonsRideTheHitOnlyWhenNonEmpty() {
        OcsSearchResult.Hit two = hit("1", "Chunky Knit Blanket", "desc");
        two.getDocument().getData().put("seasons", List.of("autumn", "winter"));
        OcsSearchResult.Hit bare = hit("2", "Garden Parasol", "desc");
        bare.getDocument().getData().put("seasons", "summer"); // a single value can arrive bare
        OcsSearchResult.Hit empty = hit("3", "Hand Plane", "desc");
        empty.getDocument().getData().put("seasons", List.of());
        OcsSearchResult.Hit preReindex = hit("4", "Tool Chest", "desc"); // field absent

        Mockito.when(searchClient.search(anyString(), anyInt(), anyInt(), any(), any()))
                .thenReturn(resultWithHits(two, bare, empty, preReindex));

        List<Map<String, Object>> items = goodsList(service.search("q", 1, 20, null, Map.of()));
        assertThat(items.get(0)).containsEntry("seasons", List.of("autumn", "winter"));
        assertThat(items.get(1)).containsEntry("seasons", List.of("summer"));
        // Empty and absent are indistinguishable to a card and both mean "no season badge".
        assertThat(items.get(2)).doesNotContainKey("seasons");
        assertThat(items.get(3)).doesNotContainKey("seasons");
    }

    // ---- Wave-21 groupon_flag passthrough ---------------------------------

    @Test
    public void grouponFlagRidesTheHitOnlyWhenPositive() {
        OcsSearchResult.Hit flagged = hit("1", "Silk Scarf", "desc");
        flagged.getDocument().getData().put("groupon_flag", 1);
        OcsSearchResult.Hit unflagged = hit("2", "Cotton Socks", "desc");
        unflagged.getDocument().getData().put("groupon_flag", 0);
        OcsSearchResult.Hit preReindex = hit("3", "Wool Hat", "desc"); // field absent

        Mockito.when(searchClient.search(anyString(), anyInt(), anyInt(), any(), any()))
                .thenReturn(resultWithHits(flagged, unflagged, preReindex));

        List<Map<String, Object>> items = goodsList(service.search("q", 1, 20, null, Map.of()));
        assertThat(items.get(0)).containsEntry("groupon_flag", 1);
        assertThat(items.get(1)).doesNotContainKey("groupon_flag");
        assertThat(items.get(2)).doesNotContainKey("groupon_flag");
    }

    // ---- highlighting -----------------------------------------------------

    @Test
    public void searchAttachesAppSideHighlightMapToMatchingHits() {
        Mockito.when(searchClient.search(anyString(), anyInt(), anyInt(), any(), any()))
                .thenReturn(resultWithHits(
                        hit("1", "Ice Silk Boxer Briefs", "Breathable silk fabric"),
                        hit("2", "Cotton Socks", "Plain weave")));

        Map<String, Object> response = service.search("silk", 1, 20, null, Map.of());

        List<Map<String, Object>> items = goodsList(response);
        Map<String, String> first = (Map<String, String>) items.get(0).get("highlight");
        assertThat(first).containsEntry("name", "Ice <em>Silk</em> Boxer Briefs");
        assertThat(first).containsEntry("brief", "Breathable <em>silk</em> fabric");
        // The non-matching hit carries NO highlight key at all (contract: absent map => plain).
        assertThat(items.get(1)).doesNotContainKey("highlight");
    }

    @Test
    public void blankQueryProducesNoHighlightMaps() {
        Mockito.when(searchClient.search(any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(resultWithHits(hit("1", "Ice Silk Boxer Briefs", null)));

        Map<String, Object> response = service.search("", 1, 20, null, Map.of());

        assertThat(goodsList(response).get(0)).doesNotContainKey("highlight");
    }

    @Test
    public void ocsProvidedHighlightIsPreferredNormalizedAndFieldRemapped() {
        OcsSearchResult.Hit hit = hit("1", "Ice Silk Boxer Briefs", "desc");
        Map<String, Object> ocsHighlight = new HashMap<>();
        ocsHighlight.put("title", "Ice <em>Silk</em> <b onclick=\"x\">Boxer</b> Briefs");
        ocsHighlight.put("unknown_field", "<em>Silk</em>");
        ocsHighlight.put("description", 42);
        hit.setHighlight(ocsHighlight);

        Mockito.when(searchClient.search(anyString(), anyInt(), anyInt(), any(), any()))
                .thenReturn(resultWithHits(hit));

        Map<String, Object> response = service.search("silk", 1, 20, null, Map.of());

        Map<String, String> highlight = (Map<String, String>) goodsList(response).get(0).get("highlight");
        // title -> name, only <em> survives, unknown/non-string entries dropped.
        assertThat(highlight).containsEntry("name", "Ice <em>Silk</em> Boxer Briefs");
        assertThat(highlight).hasSize(1);
    }

    @Test
    public void highlightFailureIsSoftAndLeavesHitsPlain() {
        SearchHighlighter throwing = Mockito.mock(SearchHighlighter.class);
        Mockito.when(throwing.highlight(anyString(), any())).thenThrow(new RuntimeException("boom"));
        service = new SearchService(searchClient, suggestClient, commentStatsService,
                throwing, categoryNameResolver, searchKeywordService);
        Mockito.when(searchClient.search(anyString(), anyInt(), anyInt(), any(), any()))
                .thenReturn(resultWithHits(hit("1", "Ice Silk Boxer Briefs", null)));

        Map<String, Object> response = service.search("silk", 1, 20, null, Map.of());

        List<Map<String, Object>> items = goodsList(response);
        assertThat(items).hasSize(1);
        assertThat(items.get(0)).doesNotContainKey("highlight");
        assertThat(items.get(0).get("name")).isEqualTo("Ice Silk Boxer Briefs");
    }

    // ---- typed suggest ----------------------------------------------------

    @Test
    public void suggestTypesEntriesBySourceFieldAndResolvesCategoryIds() {
        Mockito.when(suggestClient.suggest("wom")).thenReturn(Arrays.asList(
                suggestion("Women's Shoes", "category_names"),
                suggestion("Womens Quick-Dry Shirt", "title")));
        Mockito.when(categoryNameResolver.resolveId("Women's Shoes")).thenReturn(1036);

        List<Map<String, Object>> entries = service.suggest("wom");

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0)).containsEntry("text", "Women's Shoes")
                .containsEntry("type", "category").containsEntry("categoryId", 1036);
        assertThat(entries.get(1)).containsEntry("text", "Womens Quick-Dry Shirt")
                .containsEntry("type", "keyword").doesNotContainKey("categoryId");
    }

    @Test
    public void categorySourcedEntryThatDoesNotResolveDegradesToKeyword() {
        Mockito.when(suggestClient.suggest("und")).thenReturn(
                List.of(suggestion("Underwear", "category_names")));
        Mockito.when(categoryNameResolver.resolveId("Underwear")).thenReturn(null);

        List<Map<String, Object>> entries = service.suggest("und");

        assertThat(entries.get(0)).containsEntry("type", "keyword").doesNotContainKey("categoryId");
    }

    @Test
    public void untaggedEntryStillGetsAResolutionAttempt() {
        Mockito.when(suggestClient.suggest("sho")).thenReturn(
                List.of(suggestion("Women's Shoes", null)));
        Mockito.when(categoryNameResolver.resolveId("Women's Shoes")).thenReturn(1036);

        List<Map<String, Object>> entries = service.suggest("sho");

        assertThat(entries.get(0)).containsEntry("type", "category").containsEntry("categoryId", 1036);
    }

    @Test
    public void curatedKeywordsMergeIntoTheSameResponseDeduplicated() {
        Mockito.when(suggestClient.suggest("qui")).thenReturn(
                List.of(suggestion("quilt pillow", "title")));
        Mockito.when(searchKeywordService.helper("qui", 1, 3))
                .thenReturn(Arrays.asList("Quilt Pillow", "quilt covers"));

        List<Map<String, Object>> entries = service.suggest("qui");

        // "Quilt Pillow" collides case-insensitively with the OCS entry and is dropped.
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0)).containsEntry("text", "quilt pillow").containsEntry("type", "keyword");
        assertThat(entries.get(1)).containsEntry("text", "quilt covers").containsEntry("type", "curated");
    }

    @Test
    public void ocsSuggestOutageDegradesToCuratedOnly() {
        Mockito.when(suggestClient.suggest(anyString()))
                .thenThrow(new ResourceAccessException("connection refused"));
        Mockito.when(searchKeywordService.helper("qui", 1, 3)).thenReturn(List.of("quilt covers"));

        List<Map<String, Object>> entries = service.suggest("qui");

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0)).containsEntry("text", "quilt covers").containsEntry("type", "curated");
    }

    @Test
    public void curatedLookupFailureStillReturnsOcsEntries() {
        Mockito.when(suggestClient.suggest("qui")).thenReturn(
                List.of(suggestion("quilt pillow", "title")));
        Mockito.when(searchKeywordService.helper(anyString(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("db down"));

        List<Map<String, Object>> entries = service.suggest("qui");

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0)).containsEntry("text", "quilt pillow");
    }

    @Test
    public void blankAndDuplicatePhrasesAreDropped() {
        Mockito.when(suggestClient.suggest("sh")).thenReturn(Arrays.asList(
                suggestion("T-Shirts", "title"),
                suggestion("t-shirts", "title"),
                suggestion("  ", "title"),
                suggestion(null, "title")));

        List<Map<String, Object>> entries = service.suggest("sh");

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0)).containsEntry("text", "T-Shirts");
    }
}
