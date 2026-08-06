package org.linlinjava.litemall.promotion.infrastructure.services;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.LitemallCategoryMapper;
import org.linlinjava.litemall.db.dao.LitemallGoodsMapper;
import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.domain.LitemallCategoryExample;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallGoodsExample;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave-18 ancestor-aware scope facts: derive cart categories from goods ids
 * (one batched read), walk them up the litemall_category pid chain, and stay
 * loop-safe. Mappers are faked with JDK proxies over an in-memory tree
 * (goods 10008302 -> leaf 1301 -> L1 1300; goods 20 -> leaf 2501 -> L1 2500).
 */
class CouponScopeExpanderTest {

    /** goodsId -> leaf categoryId */
    private final Map<Integer, Integer> goodsCategory = Map.of(
            10008302, 1301,
            10008303, 1301,
            20, 2501);

    /** categoryId -> pid (0 = root) */
    private final Map<Integer, Integer> categoryPid = Map.of(
            1301, 1300,
            1300, 0,
            2501, 2500,
            2500, 0);

    @SuppressWarnings("unchecked")
    private static List<Integer> inValues(Object example) {
        // Both generated Example classes expose getOredCriteria() -> Criteria
        // -> getAllCriteria() -> Criterion{condition,value}; pull the id-in list.
        try {
            Object criteriaList = example.getClass().getMethod("getOredCriteria").invoke(example);
            for (Object criteria : (List<Object>) criteriaList) {
                Object criterions = criteria.getClass().getMethod("getAllCriteria").invoke(criteria);
                for (Object criterion : (List<Object>) criterions) {
                    String condition = (String) criterion.getClass().getMethod("getCondition").invoke(criterion);
                    if (condition != null && condition.startsWith("id in")) {
                        return (List<Integer>) criterion.getClass().getMethod("getValue").invoke(criterion);
                    }
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return List.of();
    }

    private LitemallGoodsMapper goodsMapper() {
        return (LitemallGoodsMapper) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{LitemallGoodsMapper.class},
                (proxy, method, args) -> {
                    if ("selectByExampleSelective".equals(method.getName())) {
                        List<LitemallGoods> rows = new ArrayList<>();
                        for (Integer id : inValues(args[0])) {
                            Integer categoryId = goodsCategory.get(id);
                            if (categoryId != null) {
                                LitemallGoods goods = new LitemallGoods();
                                goods.setId(id);
                                goods.setCategoryId(categoryId);
                                rows.add(goods);
                            }
                        }
                        return rows;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private LitemallCategoryMapper categoryMapper() {
        return (LitemallCategoryMapper) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{LitemallCategoryMapper.class},
                (proxy, method, args) -> {
                    if ("selectByExampleSelective".equals(method.getName())) {
                        List<LitemallCategory> rows = new ArrayList<>();
                        for (Integer id : inValues(args[0])) {
                            Integer pid = categoryPid.get(id);
                            if (pid != null) {
                                LitemallCategory category = new LitemallCategory();
                                category.setId(id);
                                category.setPid(pid);
                                rows.add(category);
                            }
                        }
                        return rows;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private CouponScopeExpander expander() {
        return new CouponScopeExpander(goodsMapper(), categoryMapper());
    }

    @Test
    void explicitLeafCategoriesGainTheirAncestors() {
        List<Integer> expanded = expander().expandCategoryIds(null, List.of(1301));
        assertEquals(Set.of(1301, 1300), new HashSet<>(expanded));
    }

    @Test
    void categoriesDerivedFromGoodsWhenOmitted() {
        List<Integer> expanded = expander().expandCategoryIds(List.of(10008302, 10008303), null);
        assertEquals(Set.of(1301, 1300), new HashSet<>(expanded));
    }

    @Test
    void derivedAndExplicitPathsAgree() {
        // The selectlist caller (goods ids only) and the order-submit caller
        // (leaf category ids) must resolve the SAME expanded set.
        List<Integer> derived = expander().expandCategoryIds(List.of(10008302), List.of());
        List<Integer> explicit = expander().expandCategoryIds(null, List.of(1301));
        assertEquals(new HashSet<>(explicit), new HashSet<>(derived));
    }

    @Test
    void mixedCartCoversEveryChain() {
        List<Integer> expanded = expander().expandCategoryIds(List.of(10008302, 20), null);
        assertEquals(Set.of(1301, 1300, 2501, 2500), new HashSet<>(expanded));
    }

    @Test
    void emptyInputsYieldEmptyList() {
        assertTrue(expander().expandCategoryIds(null, null).isEmpty());
        assertTrue(expander().expandCategoryIds(List.of(), List.of()).isEmpty());
    }

    @Test
    void unknownIdsAreKeptButNotExpanded() {
        // A category id the table doesn't know stays in the set (matchesGoods
        // simply won't find a coupon scoped to it) without breaking the walk.
        List<Integer> expanded = expander().expandCategoryIds(null, List.of(999999, 1301));
        assertEquals(Set.of(999999, 1301, 1300), new HashSet<>(expanded));
    }
}
