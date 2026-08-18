package org.linlinjava.litemall.goods.application.content;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.PageMapper;
import org.linlinjava.litemall.db.domain.LitemallPage;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wave-20 page service semantics: clone always lands a fresh DRAFT custom
 * page ("Copy of …", category + config inherited, {@code is_template} and
 * active status never copied); the admin list forwards the new
 * category/template filters and exposes both fields on rows.
 */
public class PageServiceTest {

    private PageMapper pageMapper;
    private PageService service;

    @BeforeEach
    public void setUp() {
        pageMapper = mock(PageMapper.class);
        service = new PageService(pageMapper, new ObjectMapper());
    }

    private static LitemallPage page(Integer id, String name, String position, String category,
                                     String status, boolean template) {
        LitemallPage p = new LitemallPage();
        p.setId(id);
        p.setName(name);
        p.setPosition(position);
        p.setCategory(category);
        p.setConfig("{\"version\":1,\"components\":[]}");
        p.setStatus(status);
        p.setIsTemplate(template);
        p.setAddTime(LocalDateTime.now());
        p.setUpdateTime(LocalDateTime.now());
        p.setDeleted(false);
        return p;
    }

    // ---------------- clone ----------------

    @Test
    public void cloneOfATemplateIsADraftCustomNonTemplateCopy() {
        LitemallPage source = page(7, "Coupon spotlight", LitemallPage.POSITION_CUSTOM,
                LitemallPage.CATEGORY_COUPON, LitemallPage.STATUS_DRAFT, true);
        when(pageMapper.selectById(7)).thenReturn(source);

        LitemallPage copy = service.clone(7);

        ArgumentCaptor<LitemallPage> captor = ArgumentCaptor.forClass(LitemallPage.class);
        verify(pageMapper).insert(captor.capture());
        LitemallPage inserted = captor.getValue();
        assertThat(inserted).isSameAs(copy);
        assertThat(inserted.getName()).isEqualTo("Copy of Coupon spotlight");
        assertThat(inserted.getPosition()).isEqualTo(LitemallPage.POSITION_CUSTOM);
        assertThat(inserted.getCategory()).isEqualTo(LitemallPage.CATEGORY_COUPON);
        assertThat(inserted.getConfig()).isEqualTo(source.getConfig());
        assertThat(inserted.getStatus()).isEqualTo(LitemallPage.STATUS_DRAFT);
        assertThat(inserted.getIsTemplate()).isFalse();
        assertThat(inserted.getDeleted()).isFalse();
    }

    @Test
    public void cloneOfAnActiveHomePageIsStillADraftCustomCopy() {
        // Cloning ANY page is allowed — the copy never inherits home position
        // or active status (single-active-home invariant untouched).
        when(pageMapper.selectById(3)).thenReturn(
                page(3, "Live home", LitemallPage.POSITION_HOME, LitemallPage.CATEGORY_GENERAL,
                        LitemallPage.STATUS_ACTIVE, false));

        LitemallPage copy = service.clone(3);

        assertThat(copy.getPosition()).isEqualTo(LitemallPage.POSITION_CUSTOM);
        assertThat(copy.getStatus()).isEqualTo(LitemallPage.STATUS_DRAFT);
        assertThat(copy.getIsTemplate()).isFalse();
    }

    @Test
    public void cloneTruncatesTheCopyNameToTheColumnLimit() {
        String longName = "x".repeat(63);
        when(pageMapper.selectById(9)).thenReturn(
                page(9, longName, LitemallPage.POSITION_CUSTOM, LitemallPage.CATEGORY_GROUPON,
                        LitemallPage.STATUS_DRAFT, true));

        LitemallPage copy = service.clone(9);

        assertThat(copy.getName()).hasSize(63).startsWith("Copy of xxx");
    }

    @Test
    public void cloneOfAMissingPageReturnsNull() {
        when(pageMapper.selectById(404)).thenReturn(null);
        assertThat(service.clone(404)).isNull();
    }

    // ---------------- list / read ----------------

    @Test
    public void adminListForwardsFiltersAndExposesCategoryAndTemplateFlag() {
        when(pageMapper.selectAdminPage("custom", "draft", "coupon", true, 0, 10))
                .thenReturn(List.of(page(7, "Coupon spotlight", "custom", "coupon", "draft", true)));
        when(pageMapper.countAdmin("custom", "draft", "coupon", true)).thenReturn(1);

        Map<String, Object> data = service.adminList("custom", "draft", "coupon", true, 1, 10);

        assertThat(data.get("total")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> row = ((List<Map<String, Object>>) data.get("list")).get(0);
        assertThat(row.get("category")).isEqualTo("coupon");
        assertThat(row.get("isTemplate")).isEqualTo(true);
    }

    @Test
    public void adminReadCarriesCategoryAndTemplateFlag() {
        Map<String, Object> vo = service.adminRead(
                page(8, "Group-buy rally", "custom", "groupon", "draft", true));
        assertThat(vo.get("category")).isEqualTo("groupon");
        assertThat(vo.get("isTemplate")).isEqualTo(true);
    }

    // ---------------- create / update ----------------

    @Test
    public void createPersistsCategoryAndNeverATemplate() {
        service.create("Sale page", LitemallPage.POSITION_CUSTOM, LitemallPage.CATEGORY_COUPON,
                "{\"version\":1,\"components\":[]}");

        ArgumentCaptor<LitemallPage> captor = ArgumentCaptor.forClass(LitemallPage.class);
        verify(pageMapper).insert(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo(LitemallPage.CATEGORY_COUPON);
        assertThat(captor.getValue().getIsTemplate()).isFalse();
        assertThat(captor.getValue().getStatus()).isEqualTo(LitemallPage.STATUS_DRAFT);
    }

    @Test
    public void updatePatchesCategorySelectively() {
        when(pageMapper.updateSelective(org.mockito.ArgumentMatchers.any())).thenReturn(1);

        service.update(5, null, LitemallPage.CATEGORY_GROUPON, null);

        ArgumentCaptor<LitemallPage> captor = ArgumentCaptor.forClass(LitemallPage.class);
        verify(pageMapper).updateSelective(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo(LitemallPage.CATEGORY_GROUPON);
        assertThat(captor.getValue().getName()).isNull();
        assertThat(captor.getValue().getConfig()).isNull();
        assertThat(captor.getValue().getIsTemplate()).isNull();
    }

    // ---------------- season collection (Wave 27) ----------------

    @Test
    public void activeByCategoryServesTheSeasonPageView() {
        LitemallPage season = page(31, "Autumn spotlight", LitemallPage.POSITION_CUSTOM,
                LitemallPage.CATEGORY_SEASON, LitemallPage.STATUS_ACTIVE, false);
        when(pageMapper.selectActiveByCategory(LitemallPage.CATEGORY_SEASON)).thenReturn(season);

        Map<String, Object> view = service.activeByCategory(LitemallPage.CATEGORY_SEASON);

        assertThat(view).isNotNull();
        assertThat(view.get("id")).isEqualTo(31);
        assertThat(view.get("name")).isEqualTo("Autumn spotlight");
        assertThat(view.get("category")).isEqualTo(LitemallPage.CATEGORY_SEASON);
    }

    /**
     * No season running is a NORMAL state, not a failure: null here becomes errno 642 at the
     * edge and an ABSENT strip in the storefront. If this ever threw or fabricated an empty
     * page, the SPA would render a dead nav link to nothing.
     */
    @Test
    public void noActiveSeasonPageIsNullRatherThanAnEmptyView() {
        when(pageMapper.selectActiveByCategory(LitemallPage.CATEGORY_SEASON)).thenReturn(null);

        assertThat(service.activeByCategory(LitemallPage.CATEGORY_SEASON)).isNull();
    }

    /** The category is passed through verbatim — the season route must not read the home slot. */
    @Test
    public void activeByCategoryQueriesTheRequestedCategoryOnly() {
        service.activeByCategory(LitemallPage.CATEGORY_SEASON);

        verify(pageMapper).selectActiveByCategory(LitemallPage.CATEGORY_SEASON);
        verify(pageMapper, org.mockito.Mockito.never()).selectActiveHome();
    }
}
