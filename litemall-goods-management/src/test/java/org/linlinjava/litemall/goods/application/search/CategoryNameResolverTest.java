package org.linlinjava.litemall.goods.application.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.service.LitemallCategoryService;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;

public class CategoryNameResolverTest {

    private LitemallCategoryService categoryService;
    private CategoryNameResolver resolver;

    @BeforeEach
    void setup() {
        categoryService = Mockito.mock(LitemallCategoryService.class);
        resolver = new CategoryNameResolver(categoryService);
    }

    private static LitemallCategory category(int id, String name) {
        LitemallCategory c = new LitemallCategory();
        c.setId(id);
        c.setName(name);
        return c;
    }

    private void stubCategories(List<LitemallCategory> categories) {
        Mockito.when(categoryService.querySelective(isNull(), isNull(), any(), any(), isNull(), isNull()))
                .thenReturn(categories);
    }

    @Test
    public void resolvesExactNameCaseInsensitivelyAndTrimmed() {
        stubCategories(Arrays.asList(category(7, "Women's Shoes"), category(9, "T-Shirts")));

        assertThat(resolver.resolveId("Women's Shoes")).isEqualTo(7);
        assertThat(resolver.resolveId("women's shoes")).isEqualTo(7);
        assertThat(resolver.resolveId("  T-Shirts  ")).isEqualTo(9);
    }

    @Test
    public void unknownNameResolvesToNull() {
        stubCategories(List.of(category(7, "Women's Shoes")));

        assertThat(resolver.resolveId("Menswear")).isNull();
        assertThat(resolver.resolveId(null)).isNull();
        assertThat(resolver.resolveId("  ")).isNull();
    }

    @Test
    public void ambiguousDuplicateNameResolvesToNull() {
        // The known live condition: two sibling-ish categories sharing one name.
        stubCategories(Arrays.asList(category(3, "Underwear"), category(8, "Underwear"), category(5, "Socks")));

        assertThat(resolver.resolveId("Underwear")).isNull();
        assertThat(resolver.resolveId("Socks")).isEqualTo(5);
    }

    @Test
    public void snapshotIsCachedAcrossLookups() {
        stubCategories(List.of(category(7, "Women's Shoes")));

        resolver.resolveId("Women's Shoes");
        resolver.resolveId("Women's Shoes");
        resolver.resolveId("something else");

        Mockito.verify(categoryService, Mockito.times(1))
                .querySelective(isNull(), isNull(), any(), any(), isNull(), isNull());
    }

    @Test
    public void loadFailureDegradesToNullWithoutThrowing() {
        Mockito.when(categoryService.querySelective(isNull(), isNull(), any(), any(), isNull(), isNull()))
                .thenThrow(new RuntimeException("db down"));

        assertThat(resolver.resolveId("Women's Shoes")).isNull();
    }

    @Test
    public void rowsWithMissingNameOrIdAreSkipped() {
        stubCategories(Arrays.asList(category(7, "Women's Shoes"), category(11, null), new LitemallCategory()));

        assertThat(resolver.resolveId("Women's Shoes")).isEqualTo(7);
    }
}
