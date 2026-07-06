package org.linlinjava.litemall.goods.application.search;

import org.linlinjava.litemall.db.dao.LitemallCjLinkageMapper;
import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.service.LitemallCategoryService;
import org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.cjcategory.CJCategoryDataResponse;
import org.linlinjava.litemall.goods.infrastructure.acl.service.cjdropshipservice.api.product.CJProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Mirrors CJ Dropshipping's full 3-level category tree into {@code litemall_category}, so promoted
 * CJ goods can hang off a REAL root&rarr;leaf chain instead of a flat leaf under the single
 * "Imported" bucket — which is what left the storefront's category flow with empty subcategories.
 *
 * <p>Rows are upserted by the {@code cj_category_id} natural key (unique index, V23; widened to
 * 128 chars in V26), which makes a re-sync idempotent:
 * <ul>
 *   <li>L3 (leaf): the raw CJ category UUID — the same id every {@code CJProduct} carries and the
 *       key {@code CjProductPromotionService} already resolves by
 *       ({@code LitemallCjLinkageMapper.findCjCategoryIdByCjId});</li>
 *   <li>L1: {@code L1:<normalized first-level name>} (CJ gives L1/L2 no upstream ids);</li>
 *   <li>L2: {@code L2:<normalized first>/<normalized second>}.</li>
 * </ul>
 * CJ L1s land as top-level siblings ({@code pid=0}) of the native categories, so they surface in
 * the home/channel navigation like any local L1. Levels are {@code L1}/{@code L2}/{@code L3} —
 * note {@code L3} is new to the (natively two-level) taxonomy; {@link CategorySearchService} and
 * the OCS doc builder walk the {@code pid} chain, so they pick the deeper tree up transparently.
 *
 * <p>Leaves previously created flat by the promotion service are keyed by the same CJ UUID, so
 * the upsert re-parents them onto their real L2 in place — existing goods keep their category id
 * and inherit the full chain on the next reindex. Once the "Imported" root has no live children
 * left it is soft-deleted.
 *
 * <p>The CJ tree comes from the Redis-cached {@link CJProductService#fetchCategoryList()} (one
 * cheap call), so this costs no extra CJ API quota. When the fetch fails, sync degrades to a
 * no-op and resolution keeps working off the already-persisted mirror.
 */
@Service
public class CjCategoryTreeSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CjCategoryTreeSyncService.class);

    /** Origin discriminator, aligned with the promotion service ('local' vs 'cj'). */
    private static final String SOURCE_CJ = "cj";
    /** The legacy flat root the promotion service used to hang all CJ leaves under. */
    private static final String IMPORTED_ROOT = "Imported";
    /** litemall_category.name is varchar(63). */
    private static final int MAX_NAME = 63;
    /** litemall_category.cj_category_id is varchar(128) (V26). */
    private static final int MAX_KEY = 128;

    private final CJProductService cjProductService;
    private final LitemallCategoryService categoryService;
    private final LitemallCjLinkageMapper linkageMapper;

    public CjCategoryTreeSyncService(CJProductService cjProductService,
                                     LitemallCategoryService categoryService,
                                     LitemallCjLinkageMapper linkageMapper) {
        this.cjProductService = cjProductService;
        this.categoryService = categoryService;
        this.linkageMapper = linkageMapper;
    }

    /**
     * Fetch the (cached) CJ tree and upsert every level. Safe to call on every sync/promotion run:
     * an unchanged tree is a read-only pass, and an upstream failure degrades to a no-op (resolution
     * then runs off whatever mirror is already persisted). Returns the number of live mirror nodes.
     */
    public int syncTree() {
        CJCategoryDataResponse tree = null;
        try {
            tree = cjProductService.fetchCategoryList();
        } catch (RuntimeException ex) {
            LOGGER.warn("CJ category tree fetch failed ({}); keeping persisted mirror", ex.getMessage());
        }
        if (tree == null || tree.getData() == null || tree.getData().isEmpty()) {
            return 0;
        }

        Map<String, LitemallCategory> existing = new HashMap<>();
        for (LitemallCategory row : linkageMapper.selectCjMappedCategories()) {
            existing.put(row.getCjCategoryId(), row);
        }

        int nodes = 0;
        int l1Sort = 0;
        for (CJCategoryDataResponse.CategoryData first : tree.getData()) {
            if (first == null || isBlank(first.getCategoryFirstName())) {
                continue;
            }
            String l1Name = first.getCategoryFirstName().trim();
            LitemallCategory l1 = upsert(existing, l1Key(l1Name), l1Name, 0, "L1", ++l1Sort);
            nodes++;
            if (first.getCategoryFirstList() == null) {
                continue;
            }
            int l2Sort = 0;
            for (CJCategoryDataResponse.CategorySecond second : first.getCategoryFirstList()) {
                if (second == null || isBlank(second.getCategorySecondName())) {
                    continue;
                }
                String l2Name = second.getCategorySecondName().trim();
                LitemallCategory l2 = upsert(existing, l2Key(l1Name, l2Name), l2Name, l1.getId(), "L2", ++l2Sort);
                nodes++;
                if (second.getCategorySecondList() == null) {
                    continue;
                }
                int l3Sort = 0;
                for (CJCategoryDataResponse.CategoryThird third : second.getCategorySecondList()) {
                    if (third == null || isBlank(third.getCategoryId()) || isBlank(third.getCategoryName())) {
                        continue;
                    }
                    upsert(existing, third.getCategoryId().trim(), third.getCategoryName().trim(),
                            l2.getId(), "L3", ++l3Sort);
                    nodes++;
                }
            }
        }
        retireImportedRootIfEmpty();
        LOGGER.info("CJ category tree sync: {} nodes upserted", nodes);
        return nodes;
    }

    /**
     * Resolve a CJ category-name path ("A / B / C" segments, leaf last) to the mirrored leaf's
     * local category id — the fallback for products whose leaf UUID is missing/unknown. Walks the
     * synthetic L2 key, then matches the leaf by name among that L2's children.
     */
    public Integer resolveLeafIdByPath(List<String> namePath) {
        if (namePath == null || namePath.size() < 2) {
            return null;
        }
        String l1 = namePath.get(0);
        String l2 = namePath.size() >= 3 ? namePath.get(1) : null;
        String leafName = namePath.get(namePath.size() - 1);
        LitemallCategory parent = (l2 != null)
                ? linkageMapper.findCategoryByCjKey(truncate(l2Key(l1, l2), MAX_KEY))
                : linkageMapper.findCategoryByCjKey(truncate(l1Key(l1), MAX_KEY));
        if (parent == null || Boolean.TRUE.equals(parent.getDeleted())) {
            return null;
        }
        for (LitemallCategory child : categoryService.queryByPid(parent.getId())) {
            if (child.getName() != null && child.getName().equalsIgnoreCase(leafName.trim())) {
                return child.getId();
            }
        }
        return null;
    }

    // ---- upsert -----------------------------------------------------------------------------------

    private LitemallCategory upsert(Map<String, LitemallCategory> existing, String key, String name,
                                    Integer pid, String level, int sortOrder) {
        String naturalKey = truncate(key, MAX_KEY);
        String rowName = truncate(name, MAX_NAME);

        LitemallCategory row = existing.get(naturalKey);
        if (row == null) {
            row = new LitemallCategory();
            row.setCjCategoryId(naturalKey);
            row.setName(rowName);
            row.setPid(pid);
            row.setLevel(level);
            row.setSortOrder((byte) Math.min(sortOrder, Byte.MAX_VALUE));
            row.setSource(SOURCE_CJ);
            row.setDeleted(false);
            categoryService.add(row); // insertSelective backfills the generated id
            existing.put(naturalKey, row);
            return row;
        }
        boolean dirty = !Objects.equals(row.getName(), rowName)
                || !Objects.equals(row.getPid(), pid)
                || !Objects.equals(row.getLevel(), level)
                || Boolean.TRUE.equals(row.getDeleted());
        if (dirty) {
            row.setName(rowName);
            row.setPid(pid);
            row.setLevel(level);
            row.setDeleted(false); // resurrect a soft-deleted mirror row
            categoryService.updateById(row);
        }
        return row;
    }

    /**
     * The pre-tree promotion flow hung every CJ leaf under one flat "Imported" L1. After the mirror
     * re-parents the UUID-keyed leaves onto their real L2s, that root usually ends up childless —
     * soft-delete it then, so it stops showing as an empty channel in the storefront nav. (Leaves
     * created by NAME only — products that carried no CJ category id — keep it alive until they are
     * re-promoted.)
     */
    private void retireImportedRootIfEmpty() {
        Integer rootId = linkageMapper.findCjCategoryIdByName(IMPORTED_ROOT);
        if (rootId == null) {
            return;
        }
        LitemallCategory root = categoryService.findById(rootId);
        if (root == null || !"L1".equalsIgnoreCase(root.getLevel())) {
            return; // only ever retire the legacy ROOT, never a same-named leaf
        }
        if (categoryService.queryByPid(rootId).isEmpty()) {
            categoryService.deleteById(rootId);
            LOGGER.info("Retired empty legacy 'Imported' CJ root (category id {})", rootId);
        }
    }

    // ---- keys -------------------------------------------------------------------------------------

    static String l1Key(String firstName) {
        return "L1:" + norm(firstName);
    }

    static String l2Key(String firstName, String secondName) {
        return "L2:" + norm(firstName) + "/" + norm(secondName);
    }

    private static String norm(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String truncate(String value, int max) {
        return (value != null && value.length() > max) ? value.substring(0, max) : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
