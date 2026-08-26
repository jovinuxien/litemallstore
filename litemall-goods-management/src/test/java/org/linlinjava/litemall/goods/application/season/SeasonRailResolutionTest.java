package org.linlinjava.litemall.goods.application.season;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.PageMapper;
import org.linlinjava.litemall.db.domain.LitemallPage;
import org.linlinjava.litemall.goods.application.content.PageService;
import org.linlinjava.litemall.goods.domain.service.elastic.SeasonSignalResolver;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallSeasonProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The delivery path: a {@code mode=season} rail is served as the {@code byIds} shape the current
 * storefront already renders, so the feature needs no cross-module change to be visible.
 */
public class SeasonRailResolutionTest {

    private PageMapper pageMapper;
    private SeasonSignalResolver resolver;
    private PageService service;

    @BeforeEach
    public void setUp() {
        pageMapper = mock(PageMapper.class);
        resolver = mock(SeasonSignalResolver.class);
        service = new PageService(pageMapper, new ObjectMapper(), resolver,
                new LitemallSeasonProperties());
    }

    private LitemallPage pageWith(String componentsJson) {
        LitemallPage page = new LitemallPage();
        page.setId(5);
        page.setName("Autumns Deal");
        page.setCategory("season");
        page.setStatus("active");
        page.setDeleted(false);
        page.setConfig("{\"version\":\"1.1\",\"components\":" + componentsJson + "}");
        return page;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> componentsOf(LitemallPage page) {
        when(pageMapper.selectActiveByCategory(eq("season"))).thenReturn(page);
        Map<String, Object> view = service.activeByCategory("season");
        return (List<Map<String, Object>>) view.get("components");
    }

    @Test
    public void aSeasonRailIsServedAsByIdsWithTheResolvedMembership() {
        when(resolver.publishedGoodsIds(eq("autumn"), anyInt()))
                .thenReturn(List.of(101, 102, 103));

        List<Map<String, Object>> components = componentsOf(pageWith(
                "[{\"type\":\"goods-list\",\"config\":{\"mode\":\"season\",\"seasonKey\":\"autumn\"}}]"));

        assertEquals(1, components.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) components.get(0).get("config");
        assertEquals("byIds", config.get("mode"), "served in the shape the storefront renders");
        assertEquals(List.of(101, 102, 103), config.get("goodsIds"));
        assertEquals("season", config.get("resolvedFrom"),
                "the payload stays honest that these ids were computed, not typed");
        assertEquals("autumn", config.get("seasonKey"), "the original intent survives");
    }

    /**
     * The degrade rule: a season with no members leaves NO trace. An empty grid or a heading with
     * nothing under it is exactly what the storefront's season handling forbids.
     */
    @Test
    public void aSeasonWithNoMembersDropsTheRailEntirely() {
        when(resolver.publishedGoodsIds(eq("winter"), anyInt())).thenReturn(List.of());

        List<Map<String, Object>> components = componentsOf(pageWith(
                "[{\"type\":\"goods-list\",\"config\":{\"mode\":\"season\",\"seasonKey\":\"winter\"}}]"));

        assertTrue(components.isEmpty(), "an empty season renders nothing, not an empty grid");
    }

    /** The hand-picked page must be byte-identical: this feature adds a mode, it replaces none. */
    @Test
    public void aHandPickedByIdsRailIsUntouched() {
        List<Map<String, Object>> components = componentsOf(pageWith(
                "[{\"type\":\"goods-list\",\"config\":{\"mode\":\"byIds\",\"goodsIds\":[7,8]}}]"));

        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) components.get(0).get("config");
        assertEquals("byIds", config.get("mode"));
        assertEquals(List.of(7, 8), config.get("goodsIds"));
        assertNull(config.get("resolvedFrom"), "nothing was resolved, so nothing claims it was");
    }

    @Test
    public void otherComponentTypesPassStraightThrough() {
        when(resolver.publishedGoodsIds(eq("autumn"), anyInt())).thenReturn(List.of(1));

        List<Map<String, Object>> components = componentsOf(pageWith(
                "[{\"type\":\"rich-text\",\"config\":{\"html\":\"<p>Autumn</p>\"}},"
                        + "{\"type\":\"goods-list\",\"config\":{\"mode\":\"season\",\"seasonKey\":\"autumn\"}}]"));

        assertEquals(2, components.size());
        assertEquals("rich-text", components.get(0).get("type"));
    }

    /** An explicit maxItems narrows a rail below the season cap. */
    @Test
    public void maxItemsIsHonouredWhenGiven() {
        when(resolver.publishedGoodsIds("autumn", 4)).thenReturn(List.of(1, 2, 3, 4));

        List<Map<String, Object>> components = componentsOf(pageWith(
                "[{\"type\":\"goods-list\",\"config\":{\"mode\":\"season\","
                        + "\"seasonKey\":\"autumn\",\"maxItems\":4}}]"));

        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) components.get(0).get("config");
        assertEquals(4, ((List<?>) config.get("goodsIds")).size());
    }
}
