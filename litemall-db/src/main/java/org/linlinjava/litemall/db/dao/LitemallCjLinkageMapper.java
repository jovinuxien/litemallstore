package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.domain.LitemallCjProduct;
import org.linlinjava.litemall.db.domain.LitemallGoods;

import java.util.List;

/**
 * Hand-written lookups supporting CJ → native-goods promotion. These query by the
 * {@code source} / {@code cj_pid} / {@code cj_category_id} columns added in V23, which the
 * MyBatis-Generator {@code *Example} API does not express (no generated criteria for them).
 *
 * <p>Co-located with the generated DAOs so it is picked up by both {@code @MapperScan} and
 * the {@code dao/*.xml} mapper-location pattern; no extra registration needed.
 */
public interface LitemallCjLinkageMapper {

    /** Enriched, live CJ snapshot rows ready to promote (most recently enriched first). */
    List<LitemallCjProduct> selectEnriched(@Param("limit") int limit);

    /**
     * All live CJ snapshot rows (enriched or shallow), most recently updated first. Shallow rows
     * (no real variants/attributes yet) promote into browsable native goods with a single vid-less
     * SKU; enrichment later upgrades them in place. Used for the full backfill so the whole CJ
     * catalog is visible in the storefront, not only the rate-limited enriched subset.
     */
    List<LitemallCjProduct> selectAllLive(@Param("limit") int limit);

    /** id of the native goods row already promoted from this CJ product, or null. */
    Integer findGoodsIdByCjPid(@Param("cjPid") String cjPid);

    /**
     * Like {@link #findGoodsIdByCjPid} but INCLUDING soft-deleted rows — the promote path must
     * resurrect a goods row pruned by a concurrent/earlier full sync instead of colliding with
     * the {@code uk_goods_source_cjpid} unique key on a blind insert.
     */
    Integer findAnyGoodsIdByCjPid(@Param("cjPid") String cjPid);

    /** {id, cjPid} of every live CJ-sourced goods row — used to reconcile vanished products. */
    List<LitemallGoods> findCjGoodsRefs();

    /** id of a CJ-sourced category previously created for this CJ category id, or null. */
    Integer findCjCategoryIdByCjId(@Param("cjCategoryId") String cjCategoryId);

    /**
     * Full category row by CJ natural key (leaf UUID or synthetic L1:/L2: key), INCLUDING
     * soft-deleted rows — the tree sync must resurrect them rather than collide with the
     * unique index on cj_category_id.
     */
    LitemallCategory findCategoryByCjKey(@Param("cjCategoryId") String cjCategoryId);

    /** Every category mirrored from the CJ tree (cj_category_id set), including soft-deleted. */
    List<LitemallCategory> selectCjMappedCategories();

    /** id of a CJ-sourced category with this name (root or leaf), or null. */
    Integer findCjCategoryIdByName(@Param("name") String name);

    /** id of a CJ-sourced brand with this name, or null. */
    Integer findCjBrandIdByName(@Param("name") String name);
}