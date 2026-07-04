package org.linlinjava.litemall.goods.application.search;

import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.service.LitemallCategoryService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Fills blank {@code icon_url}/{@code pic_url} on categories from a representative on-sale goods
 * image in their subtree — CJ's APIs carry no category imagery (neither {@code getCategory} nor
 * {@code product/list} has a category-image field), so mirrored CJ categories arrive imageless
 * while the native taxonomy ships curated art.
 *
 * <p>Resolution: a leaf takes its newest on-sale goods' {@code pic_url}; a parent takes the first
 * image resolved among its children (walked in {@code sort_order} as returned by
 * {@link LitemallCategoryService#queryByPid}). ONLY blank categories are written — curated native
 * images are never overwritten, and re-runs are idempotent (a filled category is skipped, an
 * still-empty subtree stays blank until goods land and a later pass fills it).
 *
 * <p>Runs after every CJ promote cycle ({@link CjCatalogRefreshTask} cron/startup and the admin
 * {@code POST /srv/private/admin/search/cj-fetch}), where new goods may have just landed in
 * previously-empty subtrees.
 */
@Service
public class CategoryImageBackfillService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CategoryImageBackfillService.class);

    private final LitemallCategoryService categoryService;
    private final LitemallGoodsService goodsService;

    public CategoryImageBackfillService(LitemallCategoryService categoryService,
                                        LitemallGoodsService goodsService) {
        this.categoryService = categoryService;
        this.goodsService = goodsService;
    }

    /** Walks every root category tree; returns how many categories were given an image. */
    public int backfillAll() {
        int filled = 0;
        for (LitemallCategory root : categoryService.queryL1()) {
            filled += fill(root).filledCount;
        }
        if (filled > 0) {
            LOGGER.info("Category image backfill: {} categories received an image", filled);
        }
        return filled;
    }

    private record Result(String image, int filledCount) {
    }

    /**
     * Depth-first: resolve children before the node itself, so a parent can inherit the first
     * image found underneath. Returns the image now representing this subtree (existing or newly
     * set, null when the whole subtree has neither goods nor art).
     */
    private Result fill(LitemallCategory node) {
        int filled = 0;
        String childImage = null;
        List<LitemallCategory> children = categoryService.queryByPid(node.getId());
        for (LitemallCategory child : children) {
            Result r = fill(child);
            filled += r.filledCount;
            if (childImage == null && r.image != null) {
                childImage = r.image;
            }
        }

        if (StringUtils.hasText(node.getPicUrl())) {
            return new Result(node.getPicUrl(), filled);
        }

        String image = childImage != null ? childImage : firstGoodsImage(node.getId());
        if (image == null) {
            return new Result(null, filled);
        }
        LitemallCategory update = new LitemallCategory();
        update.setId(node.getId());
        update.setPicUrl(image);
        if (!StringUtils.hasText(node.getIconUrl())) {
            update.setIconUrl(image);
        }
        categoryService.updateById(update);
        return new Result(image, filled + 1);
    }

    private String firstGoodsImage(Integer categoryId) {
        List<LitemallGoods> goods = goodsService.queryByCategory(categoryId, 0, 1);
        for (LitemallGoods g : goods) {
            if (StringUtils.hasText(g.getPicUrl())) {
                return g.getPicUrl();
            }
        }
        return null;
    }
}
