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

    /**
     * Full brand/store row by provider identity (V60 attribution), INCLUDING soft-deleted rows —
     * the provider upsert must resurrect a deleted row rather than collide with
     * {@code uk_brand_source_external} on a blind insert.
     */
    org.linlinjava.litemall.db.domain.LitemallBrand findBrandBySourceAndExternalId(
            @Param("source") String source, @Param("externalId") String externalId);

    /**
     * {@code source} of the live brand row this goods currently points at, or null when the goods
     * is unattributed (brand_id 0) or the brand row is gone. Drives the manual-wins rule: an
     * AttributionProvider may only (re)link goods whose current attribution is absent or
     * provider-owned — never a manual admin assignment.
     */
    String findBrandSourceOfGoods(@Param("goodsId") Integer goodsId);

    /**
     * On-sale, non-deleted goods count per brand id (one grouped query). Rows are
     * {@code {brandId, goodsCount}} maps; brands with zero goods are simply absent.
     */
    List<java.util.Map<String, Object>> countOnSaleGoodsByBrand(@Param("brandIds") List<Integer> brandIds);

    /**
     * Of the given goods ids, the subset that is live (on sale, not deleted) — one query,
     * regardless of how many callers asked. Used to count a topic's curated goods without
     * opening each topic (a topic's goods are a JSON id array on {@code litemall_topic.goods},
     * not a foreign key, so they cannot be grouped in SQL the way brand ids can).
     *
     * <p>The filter matches {@code LitemallGoodsService#findByIdVO} exactly, which is what
     * {@code /srv/topic/detail} renders with.
     */
    List<Integer> selectOnSaleGoodsIds(@Param("goodsIds") List<Integer> goodsIds);

    /**
     * Write the V31 ranking signals onto a native goods row without touching the generated
     * insert/update (which don't carry these columns). Each argument is COALESCEd against the
     * existing column, so a null leaves that signal unchanged — the CJ promote path passes all
     * four (listedNum + review aggregate + createTime), while the local review aggregator passes
     * only reviewCount/rating (listedNum + createTime null, preserved). {@code createTime}, when
     * present, also becomes the row's {@code add_time} so recency reflects the CJ product's true
     * creation date.
     */
    int updateGoodsRankingSignals(@Param("goodsId") Integer goodsId,
                                  @Param("listedNum") Integer listedNum,
                                  @Param("reviewCount") Integer reviewCount,
                                  @Param("rating") java.math.BigDecimal rating,
                                  @Param("createTime") java.time.LocalDateTime createTime);

    /**
     * Distinct LOCAL goods ids carrying at least one visible product review (type 0).
     * CJ-sourced goods are excluded (V44): their review_count/rating are enrichment-owned
     * CJ-side totals, and recomputing them from the ingested litemall_comment subset
     * (capped at ~60 rows) would clobber the true totals.
     */
    List<Integer> selectReviewedGoodsIds();

    /**
     * Stamp {@code cj_reviews_ingested_time} (V44) on a goods row — the demand-driven marker
     * that its CJ review bodies have been landed in {@code litemall_comment}. Written outside
     * the generated insert/update (which don't carry the column), same as the ranking signals.
     */
    int markCjReviewsIngested(@Param("goodsId") Integer goodsId);

    /** goods_id of the live SKU carrying this CJ variant id, or null (Wave 12 recheck path). */
    Integer findGoodsIdByCjVid(@Param("cjVid") String cjVid);

    /**
     * Write a freshly re-checked CJ warehouse stock onto the SKU row by its variant id
     * (Wave 12 nightly deal-goods recheck) — outside the generated update, which the
     * {@code *Example} API cannot address by {@code cj_vid}.
     */
    int updateProductStockByCjVid(@Param("cjVid") String cjVid, @Param("number") int number);
}