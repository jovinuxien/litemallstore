package org.linlinjava.litemall.promotion.infrastructure.services;

import org.linlinjava.litemall.db.dao.LitemallCategoryMapper;
import org.linlinjava.litemall.db.dao.LitemallGoodsMapper;
import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.domain.LitemallCategoryExample;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallGoodsExample;
import org.linlinjava.litemall.promotion.application.ports.CouponScopePort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Wave-18 scope facts from the shared litemall-db tables: derives a cart's
 * leaf category ids from its goods ids when the caller didn't pass them (one
 * batched read of {@code litemall_goods}), then walks each id up the
 * {@code litemall_category} pid chain so ANY-level coupon scoping (L1
 * encouraged) matches leaf-level carts.
 *
 * <p>NOTE: {@code andLogicalDeleted()} is inverted across litemall-db domain
 * classes — the literal is bound instead ({@code andDeletedEqualTo(false)}),
 * per the repository-impl precedent.
 */
@Component
public class CouponScopeExpander implements CouponScopePort {

    /** Hard bound on ancestor-walk depth — the tree is 2 levels today. */
    private static final int MAX_DEPTH = 10;

    private final LitemallGoodsMapper goodsMapper;
    private final LitemallCategoryMapper categoryMapper;

    public CouponScopeExpander(LitemallGoodsMapper goodsMapper,
                               LitemallCategoryMapper categoryMapper) {
        this.goodsMapper = goodsMapper;
        this.categoryMapper = categoryMapper;
    }

    @Override
    public List<Integer> expandCategoryIds(List<Integer> goodsIds, List<Integer> categoryIds) {
        List<Integer> leaves = categoryIds != null && !categoryIds.isEmpty()
                ? categoryIds
                : deriveFromGoods(goodsIds);
        return expandAncestors(leaves);
    }

    private List<Integer> deriveFromGoods(List<Integer> goodsIds) {
        List<Integer> ids = clean(goodsIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        LitemallGoodsExample example = new LitemallGoodsExample();
        example.or().andIdIn(ids).andDeletedEqualTo(false);
        Set<Integer> categoryIds = new LinkedHashSet<>();
        for (LitemallGoods goods : goodsMapper.selectByExampleSelective(example,
                LitemallGoods.Column.id, LitemallGoods.Column.categoryId)) {
            if (goods.getCategoryId() != null && goods.getCategoryId() > 0) {
                categoryIds.add(goods.getCategoryId());
            }
        }
        return new ArrayList<>(categoryIds);
    }

    private List<Integer> expandAncestors(List<Integer> categoryIds) {
        List<Integer> start = clean(categoryIds);
        if (start.isEmpty()) {
            return List.of();
        }
        Set<Integer> seen = new LinkedHashSet<>(start);
        List<Integer> frontier = start;
        for (int depth = 0; depth < MAX_DEPTH && !frontier.isEmpty(); depth++) {
            LitemallCategoryExample example = new LitemallCategoryExample();
            example.or().andIdIn(frontier).andDeletedEqualTo(false);
            List<Integer> parents = new ArrayList<>();
            for (LitemallCategory category : categoryMapper.selectByExampleSelective(example,
                    LitemallCategory.Column.id, LitemallCategory.Column.pid)) {
                Integer pid = category.getPid();
                if (pid != null && pid > 0 && seen.add(pid)) {
                    parents.add(pid);
                }
            }
            frontier = parents;
        }
        return new ArrayList<>(seen);
    }

    private static List<Integer> clean(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<Integer> distinct = new LinkedHashSet<>();
        for (Integer id : ids) {
            if (id != null && id > 0) {
                distinct.add(id);
            }
        }
        return new ArrayList<>(distinct);
    }
}
