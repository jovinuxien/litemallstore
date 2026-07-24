package org.linlinjava.litemall.goods.application.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class SearchHighlighterTest {

    private SearchHighlighter highlighter;

    @BeforeEach
    void setup() {
        highlighter = new SearchHighlighter();
    }

    private Map<String, Object> item(String name, String brief) {
        Map<String, Object> item = new HashMap<>();
        if (name != null) {
            item.put("name", name);
        }
        if (brief != null) {
            item.put("brief", brief);
        }
        return item;
    }

    @Test
    public void wrapsSingleTermMatchInEm() {
        Map<String, String> out = highlighter.highlight("silk", item("Mens Ice Silk Boxer Briefs", null));
        assertThat(out).containsEntry("name", "Mens Ice <em>Silk</em> Boxer Briefs");
    }

    @Test
    public void matchingIsCaseInsensitiveAndPreservesOriginalCasing() {
        Map<String, String> out = highlighter.highlight("SHIRT", item("Men's T-Shirts", null));
        assertThat(out).containsEntry("name", "Men's T-<em>Shirt</em>s");
    }

    @Test
    public void multiTermQueryHighlightsEveryTermAndMergesAdjacentRuns() {
        Map<String, String> out = highlighter.highlight("ice silk", item("Ice Silk Briefs", null));
        // "Ice" and "Silk" are separate matches; the space between them is unmarked.
        assertThat(out).containsEntry("name", "<em>Ice</em> <em>Silk</em> Briefs");

        Map<String, String> overlapping = highlighter.highlight("silky ilk", item("silky", null));
        // Overlapping ranges must produce one merged run, not nested/broken tags.
        assertThat(overlapping).containsEntry("name", "<em>silky</em>");
    }

    @Test
    public void briefIsHighlightedIndependentlyOfName() {
        Map<String, String> out = highlighter.highlight("quilt",
                item("Storage Box", "Fits a large quilt easily"));
        assertThat(out).doesNotContainKey("name");
        assertThat(out).containsEntry("brief", "Fits a large <em>quilt</em> easily");
    }

    @Test
    public void noMatchYieldsEmptyMap() {
        assertThat(highlighter.highlight("banana", item("Ice Silk Briefs", "Breathable"))).isEmpty();
    }

    @Test
    public void blankOrNullQueryYieldsEmptyMap() {
        assertThat(highlighter.highlight("", item("Ice Silk Briefs", null))).isEmpty();
        assertThat(highlighter.highlight("   ", item("Ice Silk Briefs", null))).isEmpty();
        assertThat(highlighter.highlight(null, item("Ice Silk Briefs", null))).isEmpty();
    }

    @Test
    public void singleCharacterTokensAreIgnored() {
        assertThat(highlighter.highlight("a b c", item("a b c cabin", null))).isEmpty();
    }

    @Test
    public void punctuationOnlyQueryYieldsEmptyMap() {
        assertThat(highlighter.highlight("!!! ???", item("Ice Silk Briefs", null))).isEmpty();
    }

    @Test
    public void nonStringAndMissingFieldsAreSkipped() {
        Map<String, Object> item = new HashMap<>();
        item.put("name", 42);
        assertThat(highlighter.highlight("silk", item)).isEmpty();
        assertThat(highlighter.highlight("silk", null)).isEmpty();
    }

    @Test
    public void sourceTextIsEmittedVerbatimOutsideEmMarkers() {
        // Escaping is the SPA's job per the contract — the snippet is raw text + <em> only.
        Map<String, String> out = highlighter.highlight("silk", item("<script> silk & co", null));
        assertThat(out).containsEntry("name", "<script> <em>silk</em> & co");
    }
}
