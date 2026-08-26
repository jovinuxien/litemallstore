package org.linlinjava.litemall.goods.application.search;

import org.linlinjava.litemall.db.dao.LitemallBrandMapper;
import org.linlinjava.litemall.db.dao.LitemallCategoryMapper;
import org.linlinjava.litemall.db.dao.LitemallCjLinkageMapper;
import org.linlinjava.litemall.db.dao.LitemallGoodsAttributeMapper;
import org.linlinjava.litemall.db.dao.LitemallGoodsMapper;
import org.linlinjava.litemall.db.dao.LitemallGoodsProductMapper;
import org.linlinjava.litemall.db.dao.LitemallGoodsSpecificationMapper;
import org.linlinjava.litemall.db.domain.LitemallBrand;
import org.linlinjava.litemall.db.domain.LitemallCategory;
import org.linlinjava.litemall.db.domain.LitemallCjProduct;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallGoodsAttribute;
import org.linlinjava.litemall.db.domain.LitemallGoodsAttributeExample;
import org.linlinjava.litemall.db.domain.LitemallGoodsProduct;
import org.linlinjava.litemall.db.domain.LitemallGoodsProductExample;
import org.linlinjava.litemall.db.domain.LitemallGoodsSpecification;
import org.linlinjava.litemall.db.domain.LitemallGoodsSpecificationExample;
import org.linlinjava.litemall.goods.application.attribution.AttributionProvider;
import org.linlinjava.litemall.goods.infrastructure.acl.adapter.CjProductToNativeAdapter;
import org.linlinjava.litemall.goods.infrastructure.acl.adapter.NativeGoodsAggregate;
import org.springframework.dao.DuplicateKeyException;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;
import org.linlinjava.litemall.goods.application.pricing.CategoryMarginResolver;
import org.linlinjava.litemall.goods.infrastructure.configuration.LitemallGoodsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Promotes enriched {@code litemall_cj_product} landing-zone rows into the native litemall
 * goods family (goods + products + specifications + attributes + category + brand), so OCS and
 * the customer SPA read a single source of truth (the DB) and checkout recovers the CJ
 * {@code vid} straight off {@code litemall_goods_product.cj_vid}.
 *
 * <p>Idempotent: goods are matched on {@code (source='cj', cj_pid)} and updated in place
 * (stable id → carts/orders never dangle); SKUs are diffed on {@code cj_vid} (matched updated,
 * new inserted, vanished soft-deleted — preserving product ids); specs/attributes are fully
 * regenerated. Reconciliation only ever touches {@code source='cj'} rows; local goods are
 * untouchable.
 *
 * <p>Each row is promoted in its own transaction via {@link TransactionTemplate} (so a single
 * bad row can't roll back a whole batch, and we avoid the {@code @Transactional}
 * self-invocation trap of looping over an annotated method in the same bean).
 */
@Service
public class CjProductPromotionService {

    private static final Logger log = LoggerFactory.getLogger(CjProductPromotionService.class);

    private static final String SOURCE_CJ = CjProductToNativeAdapter.SOURCE_CJ;
    /** Single L1 root that all CJ-sourced categories hang under, keeping the channel nav tidy. */
    private static final String IMPORTED_ROOT = "Imported";
    private static final int CATEGORY_NAME_MAX = 63;
    private static final int VARCHAR_MAX = 255;

    private final LitemallCjLinkageMapper linkageMapper;
    private final LitemallGoodsMapper goodsMapper;
    private final LitemallGoodsProductMapper productMapper;
    private final LitemallGoodsAttributeMapper attributeMapper;
    private final LitemallGoodsSpecificationMapper specificationMapper;
    private final LitemallCategoryMapper categoryMapper;
    private final LitemallBrandMapper brandMapper;
    private final org.linlinjava.litemall.db.dao.LitemallSeckillMapper seckillMapper;
    private final CjProductToNativeAdapter adapter;
    private final CjCategoryTreeSyncService categoryTreeSync;
    private final CJDropshippingConfig config;
    private final CjPricing pricing;
    private final LitemallGoodsProperties goodsProperties;
    private final CategoryMarginResolver categoryResolver;
    private final org.linlinjava.litemall.db.service.LitemallCjProductService cjProductStore;
    private final List<AttributionProvider> attributionProviders;
    private final TransactionTemplate txTemplate;

    public CjProductPromotionService(LitemallCjLinkageMapper linkageMapper,
                                     LitemallGoodsMapper goodsMapper,
                                     LitemallGoodsProductMapper productMapper,
                                     LitemallGoodsAttributeMapper attributeMapper,
                                     LitemallGoodsSpecificationMapper specificationMapper,
                                     LitemallCategoryMapper categoryMapper,
                                     LitemallBrandMapper brandMapper,
                                     org.linlinjava.litemall.db.dao.LitemallSeckillMapper seckillMapper,
                                     CjProductToNativeAdapter adapter,
                                     CjCategoryTreeSyncService categoryTreeSync,
                                     CJDropshippingConfig config,
                                     CjPricing pricing,
                                     LitemallGoodsProperties goodsProperties,
                                     CategoryMarginResolver categoryResolver,
                                     org.linlinjava.litemall.db.service.LitemallCjProductService cjProductStore,
                                     List<AttributionProvider> attributionProviders,
                                     PlatformTransactionManager transactionManager) {
        this.linkageMapper = linkageMapper;
        this.goodsMapper = goodsMapper;
        this.productMapper = productMapper;
        this.attributeMapper = attributeMapper;
        this.specificationMapper = specificationMapper;
        this.categoryMapper = categoryMapper;
        this.brandMapper = brandMapper;
        this.seckillMapper = seckillMapper;
        this.adapter = adapter;
        this.categoryTreeSync = categoryTreeSync;
        this.config = config;
        this.pricing = pricing;
        this.goodsProperties = goodsProperties;
        this.categoryResolver = categoryResolver;
        this.cjProductStore = cjProductStore;
        this.attributionProviders = attributionProviders;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Promote up to {@code limit} live CJ snapshot rows into native goods — the WHOLE catalog, not
     * just the rate-limited enriched subset, so every CJ product is browsable in the storefront.
     * Shallow rows land as browsable goods with a single vid-less SKU (ordering is blocked until they
     * are enriched and re-promoted in place). Each row runs in its own transaction; a failure is
     * logged and counted, never aborting the batch.
     */
    public PromoteResult promoteBatch(int limit) {
        List<LitemallCjProduct> rows = linkageMapper.selectAllLive(limit);
        int promoted = 0;
        int failed = 0;
        for (LitemallCjProduct row : rows) {
            try {
                Integer goodsId = txTemplate.execute(status -> promoteOne(row));
                log.debug("promoted CJ pid={} -> goods id={}", row.getPid(), goodsId);
                promoted++;
            } catch (RuntimeException ex) {
                failed++;
                log.warn("CJ promote failed for pid={}: {}", row.getPid(), ex.toString());
            }
        }
        log.info("CJ promotion batch: promoted={} failed={} of {} enriched rows", promoted, failed, rows.size());
        return new PromoteResult(promoted, failed, rows.size());
    }

    /**
     * Promote a single enriched row into native goods in its own transaction and return the native
     * goods id. Used by the enrichment flow, which holds the freshly-enriched row in hand and wants
     * it landed + indexed immediately (rather than waiting for the next batch sweep).
     */
    public Integer promote(LitemallCjProduct row) {
        return txTemplate.execute(status -> promoteOne(row));
    }

    /**
     * Soft-delete CJ-sourced goods (and their SKUs) whose {@code cj_pid} is no longer present in
     * the live snapshot. Only {@code source='cj'} rows are ever touched. Returns rows removed.
     */
    /**
     * Wave 26: minimum CJ denials before a product is treated as delisted. CJ's own not-found for a
     * specific pid, twice across separate enrichment passes — evidence about THAT product, not an
     * inference from a sampled sweep.
     */
    public static final int DELIST_MIN_STRIKES = 2;

    /**
     * Fraction of the catalogue a sweep must actually fetch before "absent" is evidence of
     * anything. Below this, reconcile declines rather than leaning on the erosion tripwire.
     */
    static final double COVERAGE_FOR_ABSENCE_INFERENCE = 0.9;

    /**
     * Soft-delete goods CJ has explicitly denied at least {@link #DELIST_MIN_STRIKES} times.
     *
     * <p>This replaces what absence-based reconcile was meant to do. It is safe where reconcile is
     * not, because the evidence is per-product: enrichment asked CJ about this exact pid and was
     * told it does not exist. The fraction tripwire is kept as a backstop — if CJ ever denies a
     * huge share of the catalogue at once, that is an upstream incident, not a mass delisting.
     */
    public int delistConfirmed() {
        return txTemplate.execute(status -> {
            List<String> pids = cjProductStore.delistedPids(DELIST_MIN_STRIKES);
            if (pids.isEmpty()) {
                return 0;
            }
            List<LitemallGoods> refs = linkageMapper.findCjGoodsRefs();
            Set<String> doomed = new HashSet<>(pids);
            List<LitemallGoods> candidates = new ArrayList<>();
            for (LitemallGoods ref : refs) {
                if (ref.getCjPid() != null && doomed.contains(ref.getCjPid())) {
                    candidates.add(ref);
                }
            }
            if (!refs.isEmpty() && !candidates.isEmpty()) {
                double fraction = (double) candidates.size() / refs.size();
                if (fraction > config.getPruneMaxFraction()) {
                    log.error("CJ delisting tripwire: CJ denied {} of {} goods ({}% > {}%) — that is an "
                                    + "upstream incident, not a mass delisting; skipped",
                            candidates.size(), refs.size(),
                            Math.round(fraction * 100), Math.round(config.getPruneMaxFraction() * 100));
                    return 0;
                }
            }
            int removed = 0;
            for (LitemallGoods ref : candidates) {
                goodsMapper.logicalDeleteByPrimaryKey(ref.getId());
                LitemallGoodsProductExample ex = new LitemallGoodsProductExample();
                ex.createCriteria().andGoodsIdEqualTo(ref.getId()).andDeletedEqualTo(false);
                for (LitemallGoodsProduct p : productMapper.selectByExample(ex)) {
                    productMapper.logicalDeleteByPrimaryKey(p.getId());
                }
                removed++;
            }
            if (removed > 0) {
                log.info("CJ delisting: soft-deleted {} goods CJ denied {}+ times", removed, DELIST_MIN_STRIKES);
            }
            return removed;
        });
    }

    public int reconcile(Set<String> liveCjPids) {
        return txTemplate.execute(status -> {
            // Two-pass: collect candidates first so the erosion tripwire can veto the whole batch.
            // "Absent from liveCjPids" is weak evidence when the fetch behind the set was partial or
            // rotation-limited — the failure mode that eroded 7.7k goods before 2026-07-13.
            List<LitemallGoods> refs = linkageMapper.findCjGoodsRefs();
            // Wave 26: absence from the sweep only means "delisted" if the sweep COVERS the
            // catalogue. It does not: the plan fetches ~25 products per leaf — a sample — while the
            // catalogue accumulated from earlier, larger plans (measured 2026-08-16: 14,426 pids
            // fetched against 33,420 goods, so 57% looked vanished and the tripwire refused, as it
            // does every week). Attempting the inference anyway just cries wolf, which would mask a
            // genuine mass-delisting. Confirmed delisting is delistConfirmed()'s job, on CJ's own
            // per-product answer.
            if (!refs.isEmpty()) {
                double coverage = (double) liveCjPids.size() / refs.size();
                if (coverage < COVERAGE_FOR_ABSENCE_INFERENCE) {
                    log.info("CJ reconcile: sweep covered {} of {} goods ({}%) — too little to read "
                                    + "absence as delisting; skipped (delisting runs on CJ's own "
                                    + "not-found answers instead)",
                            liveCjPids.size(), refs.size(), Math.round(coverage * 100));
                    return 0;
                }
            }
            List<LitemallGoods> candidates = new ArrayList<>();
            for (LitemallGoods ref : refs) {
                if (ref.getCjPid() != null && !liveCjPids.contains(ref.getCjPid())) {
                    candidates.add(ref);
                }
            }
            if (!refs.isEmpty() && !candidates.isEmpty()) {
                double fraction = (double) candidates.size() / refs.size();
                if (fraction > config.getPruneMaxFraction()) {
                    log.error("CJ reconcile tripwire: refusing to soft-delete {} of {} live CJ goods "
                                    + "({}% > {}%) — partial fetch or listing rotation suspected; reconcile skipped",
                            candidates.size(), refs.size(),
                            Math.round(fraction * 100), Math.round(config.getPruneMaxFraction() * 100));
                    return 0;
                }
            }
            int removed = 0;
            for (LitemallGoods ref : candidates) {
                goodsMapper.logicalDeleteByPrimaryKey(ref.getId());
                LitemallGoodsProductExample ex = new LitemallGoodsProductExample();
                ex.createCriteria().andGoodsIdEqualTo(ref.getId()).andDeletedEqualTo(false);
                for (LitemallGoodsProduct p : productMapper.selectByExample(ex)) {
                    productMapper.logicalDeleteByPrimaryKey(p.getId());
                }
                removed++;
            }
            log.info("CJ reconcile: soft-deleted {} goods no longer in the live snapshot", removed);
            return removed;
        });
    }

    /** Promote a single enriched row. Must run inside a transaction (see {@link #promoteBatch}). */
    Integer promoteOne(LitemallCjProduct row) {
        NativeGoodsAggregate aggregate = adapter.adapt(row);
        LitemallGoods goods = aggregate.getGoods();

        goods.setCategoryId(resolveCategoryId(aggregate.getCategory()));
        // Wave 25: attribution providers first (keyed source+external_id, curation-gated), then the
        // legacy name-keyed CJ brand-string path. A null result means "nothing to attribute" — on
        // updates the selective write then leaves the current brand_id alone (never zeroes it out).
        Integer brandId = resolveAttributedBrandId(row, aggregate.getBrand());
        goods.setBrandId(brandId);

        // Wave 14: promote is a reprice site. Once the category is resolved, retail converges on
        // cost × the category's EFFECTIVE margin (L1 override else global) in one nightly cycle —
        // the sync/enrichment already priced this way off the CJ leaf, but the whole already-landed
        // catalog reprices here when an override changes, without waiting on the enrichment rotation.
        repriceForCategory(goods, aggregate.getProducts());

        // Match INCLUDING soft-deleted rows: a full sync's stale-prune (or an off-sale edit) may
        // have soft-deleted this pid, and uk_goods_source_cjpid makes a blind re-insert collide —
        // the row must be resurrected in place instead.
        // Wave 26 Phase 2 deliverable 2: evaluate the floor AFTER repricing, against the retail the
        // customer would actually see. Withheld (price-locked) retail is read from the aggregate, so
        // a live flash deal is judged on its landed price rather than a null.
        boolean belowFloor = goodsProperties != null
                && goodsProperties.isBelowPriceFloor(goods.getRetailPrice());
        if (belowFloor) {
            log.info("CJ promote pid={}: retail {} below price floor {} — off sale (reversible)",
                    row.getPid(), goods.getRetailPrice(), goodsProperties.getPriceFloor());
        }
        // The ceiling is evaluated at the same point and for the same reason as the floor: after
        // repricing, against the retail a customer would actually see (so a live flash deal is
        // judged on its landed price). Both ends of the band gate on-sale identically below.
        boolean aboveCeiling = goodsProperties != null
                && goodsProperties.isAbovePriceCeiling(goods.getRetailPrice());
        if (aboveCeiling) {
            log.info("CJ promote pid={}: retail {} above price ceiling {} — off sale (reversible)",
                    row.getPid(), goods.getRetailPrice(), goodsProperties.getPriceCeiling());
        }
        boolean outsidePriceBand = belowFloor || aboveCeiling;

        Integer existingId = linkageMapper.findAnyGoodsIdByCjPid(goods.getCjPid());
        // Wave 12 (unparked a82a19e0e): while a flash deal is LIVE (price_swapped=1) the deal
        // engine owns the price fields — the promote path withholds retail/counter/matched-SKU
        // price writes so a nightly sync can't stomp a live swap. Stock/title/variants/COST keep
        // syncing (the floor must track the real wholesale even mid-deal); unwind re-promotes
        // from the snapshot so a mid-deal CJ reprice converges in one tick.
        boolean priceLocked = existingId != null && seckillMapper.selectLiveByGoodsId(existingId) != null;
        if (priceLocked) {
            goods.setRetailPrice(null);
            goods.setCounterPrice(null);
            log.debug("CJ promote pid={} goods={}: live flash deal — price fields withheld",
                    row.getPid(), existingId);
        }
        if (existingId != null) {
            goods.setId(existingId);
            goods.setAddTime(null); // preserve the original creation time on update
            goods.setDeleted(false); // resurrect if the row was soft-deleted
            // Wave 14: on-sale is an ADMIN decision on existing goods — the nightly promote must
            // not resurrect retired (off-sale) goods back to on-sale, so the flag is withheld on
            // updates (selective skips nulls). New inserts still land on-sale below.
            goods.setIsOnSale(null);
            // Wave 26 Phase 2 price band: policy OVERRIDES that withhold. A good priced outside
            // the band is taken off sale every cycle for as long as it stays outside —
            // deliberately, so the band is a standing rule and not a one-shot sweep an admin can
            // silently undo into a loss-making (or review-tripping) listing. Reversible: change
            // the margin, the floor or the ceiling and the next cycle puts it back.
            if (outsidePriceBand) {
                goods.setIsOnSale(false);
            }
            // Manual-wins: a source='manual' admin brand assignment is never overwritten by a
            // provider (or the legacy CJ path); withholding the field keeps it via selective update.
            if (goods.getBrandId() != null
                    && AttributionProvider.SOURCE_MANUAL.equals(linkageMapper.findBrandSourceOfGoods(existingId))) {
                goods.setBrandId(null);
            }
            goodsMapper.updateByPrimaryKeySelective(goods);
        } else {
            if (goods.getBrandId() == null) {
                goods.setBrandId(0); // unattributed sentinel, matches the pre-V60 convention
            }
            if (outsidePriceBand) {
                goods.setIsOnSale(false);   // a new out-of-band good never goes on sale at all
            }
            // Wave 26 Phase 2: while the storefront is narrowed to an anchor, a NEW good outside
            // it must not land on sale. Without this the narrowing erodes nightly — the CJ
            // pipeline deliberately keeps mirroring all 14 L1s, and THIS branch is where every
            // new product goes on sale (measured on prod one night after narrowing: 428 new
            // on-sale goods, 328 of them outside the anchor). The row and its data still land, so
            // taking a category back later still surfaces them.
            if (goodsProperties != null
                    && goodsProperties.isOutsideAnchor(categoryResolver.rootOfCategory(goods.getCategoryId()))) {
                goods.setIsOnSale(false);
                log.info("CJ promote pid={}: category {} outside the anchor — new good stays off sale",
                        row.getPid(), goods.getCategoryId());
            }
            goodsMapper.insertSelective(goods); // selectKey stamps goods.id
        }
        Integer goodsId = goods.getId();

        // V31 ranking signals: copy the enriched CJ aggregates onto the native goods row (outside the
        // generated insert/update, which don't carry these columns). createTime, when present, also
        // becomes the row's add_time so recency reflects the CJ product's true creation date. A
        // shallow (pre-enrichment) row carries nulls here — COALESCE leaves the columns at their
        // defaults until the enrichment pass fills them.
        linkageMapper.updateGoodsRankingSignals(goodsId, row.getListedNum(), row.getReviewCount(),
                row.getRating(), row.getCjCreateTime());

        upsertProducts(goodsId, aggregate.getProducts(), priceLocked);
        regenerateSpecifications(goodsId, aggregate.getSpecifications());
        regenerateAttributes(goodsId, aggregate.getAttributes());
        return goodsId;
    }

    /**
     * Wave 14: recompute retail (and counter, CJ convention counter == retail) plus per-SKU prices
     * from the captured costs with the resolved category's effective margin. No captured cost ⇒
     * the snapshot prices stand untouched (never a fake reprice). SKUs without their own cost
     * follow the product retail — same intent as the adapter's stale-basis guard.
     * Package-visible for the unit test.
     */
    void repriceForCategory(LitemallGoods goods, List<LitemallGoodsProduct> products) {
        BigDecimal cost = goods.getCost();
        if (cost == null || cost.signum() <= 0) {
            return;
        }
        BigDecimal margin = pricing.marginForCategory(goods.getCategoryId());
        BigDecimal retail = pricing.retail(cost, margin);
        goods.setRetailPrice(retail);
        goods.setCounterPrice(retail);
        for (LitemallGoodsProduct product : products) {
            BigDecimal skuCost = product.getCost();
            product.setPrice(skuCost != null && skuCost.signum() > 0
                    ? pricing.retail(skuCost, margin) : retail);
        }
    }

    /**
     * Diff SKUs by cj_vid: matched -> update (stable id), new -> insert, vanished -> soft-delete.
     * {@code priceLocked} (live flash deal) withholds price writes on matched/reused rows —
     * checkout money reads these rows, and the deal engine owns them while price_swapped=1;
     * brand-new variants still insert at snapshot price (they carry no swapped price to protect).
     */
    private void upsertProducts(Integer goodsId, List<LitemallGoodsProduct> incoming, boolean priceLocked) {
        LitemallGoodsProductExample ex = new LitemallGoodsProductExample();
        ex.createCriteria().andGoodsIdEqualTo(goodsId).andDeletedEqualTo(false);
        List<LitemallGoodsProduct> existing = productMapper.selectByExample(ex);

        Map<String, LitemallGoodsProduct> byVid = new HashMap<>();
        for (LitemallGoodsProduct p : existing) {
            byVid.put(p.getCjVid(), p); // null key allowed (synthetic no-variant SKU)
        }

        boolean incomingHasNullVid = incoming.stream().anyMatch(in -> in.getCjVid() == null);

        // Cart self-heal (Demand-Driven CJ Enrichment): a shallow goods' vid-less placeholder
        // SKUs may already sit in customer carts (they are what the submit-block 422 told the
        // customer to retry). When real variants land, reuse the placeholder ROW IDS for the
        // cheapest incoming variants (placeholders carried the "from" price, i.e. the cheapest
        // variant's price), pairing by ascending price — so existing cart lines resolve to a
        // real, orderable variant on retry instead of dangling on a soft-deleted row.
        Set<Integer> reusedPlaceholderIds = new HashSet<>();
        if (!incomingHasNullVid) {
            List<LitemallGoodsProduct> vidless = existing.stream()
                    .filter(p -> !StringUtils.hasText(p.getCjVid()))
                    .sorted(java.util.Comparator.comparing(LitemallGoodsProduct::getId))
                    .toList();
            List<LitemallGoodsProduct> newVariants = incoming.stream()
                    .filter(in -> StringUtils.hasText(in.getCjVid()) && in.getId() == null)
                    .sorted(java.util.Comparator.comparing(
                            in -> in.getPrice() == null ? new BigDecimal(Integer.MAX_VALUE) : in.getPrice()))
                    .toList();
            for (int i = 0; i < Math.min(vidless.size(), newVariants.size()); i++) {
                newVariants.get(i).setId(vidless.get(i).getId());
                reusedPlaceholderIds.add(vidless.get(i).getId());
            }
        }

        Set<String> incomingVids = new HashSet<>();
        for (LitemallGoodsProduct in : incoming) {
            in.setGoodsId(goodsId);
            if (in.getCjVid() != null) {
                incomingVids.add(in.getCjVid());
            }
            LitemallGoodsProduct match = byVid.get(in.getCjVid());
            if (match != null) {
                in.setId(match.getId());
                in.setAddTime(null); // keep original creation time
                if (priceLocked) {
                    in.setPrice(null); // selective update skips nulls — swapped price survives
                }
                productMapper.updateByPrimaryKeySelective(in);
            } else if (in.getId() != null) {
                in.setAddTime(null); // placeholder reuse: update in place, keep row id + creation time
                if (priceLocked) {
                    in.setPrice(null);
                }
                productMapper.updateByPrimaryKeySelective(in);
            } else {
                productMapper.insertSelective(in);
            }
        }
        for (LitemallGoodsProduct p : existing) {
            if (reusedPlaceholderIds.contains(p.getId())) {
                continue; // reused in place — must not be soft-deleted
            }
            String vid = p.getCjVid();
            boolean keep = vid == null ? incomingHasNullVid : incomingVids.contains(vid);
            if (!keep) {
                productMapper.logicalDeleteByPrimaryKey(p.getId());
            }
        }
    }

    private void regenerateSpecifications(Integer goodsId, List<LitemallGoodsSpecification> specs) {
        LitemallGoodsSpecificationExample ex = new LitemallGoodsSpecificationExample();
        ex.createCriteria().andGoodsIdEqualTo(goodsId);
        specificationMapper.deleteByExample(ex); // child data is fully derived; physical replace
        for (LitemallGoodsSpecification spec : specs) {
            spec.setGoodsId(goodsId);
            specificationMapper.insertSelective(spec);
        }
    }

    private void regenerateAttributes(Integer goodsId, List<LitemallGoodsAttribute> attributes) {
        LitemallGoodsAttributeExample ex = new LitemallGoodsAttributeExample();
        ex.createCriteria().andGoodsIdEqualTo(goodsId);
        attributeMapper.deleteByExample(ex);
        for (LitemallGoodsAttribute attribute : attributes) {
            attribute.setGoodsId(goodsId);
            attributeMapper.insertSelective(attribute);
        }
    }

    /**
     * Resolve the litemall_category for a CJ category descriptor, preferring the full CJ tree that
     * {@link CjCategoryTreeSyncService} mirrors into {@code litemall_category} (so the goods hangs
     * off a real root&rarr;leaf chain, which the OCS doc builder and the category search walk via
     * {@code pid}). Resolution order:
     * <ol>
     *   <li>the optional {@code category-mapping} config override — a CJ path segment pinned to a
     *       specific native category id;</li>
     *   <li>the mirrored tree by the product's leaf CJ UUID (idempotent natural key);</li>
     *   <li>the mirrored tree by name path, for products that carry no leaf UUID;</li>
     *   <li>legacy fallback (mirror never synced / unknown leaf): match by leaf name, else
     *       find-or-create the leaf flat under the "Imported" L1 root — created lazily so the
     *       retired root is not resurrected for nothing.</li>
     * </ol>
     */
    private Integer resolveCategoryId(NativeGoodsAggregate.CategoryRef cat) {
        if (cat == null) {
            return findOrCreateCategory(IMPORTED_ROOT, 0, "L1", null);
        }
        Integer override = configOverrideCategoryId(cat.getNamePath());
        if (override != null) {
            return override;
        }
        if (StringUtils.hasText(cat.getCjCategoryId())) {
            Integer byCjId = linkageMapper.findCjCategoryIdByCjId(cat.getCjCategoryId());
            if (byCjId != null) {
                return byCjId;
            }
        }
        Integer byPath = categoryTreeSync.resolveLeafIdByPath(cat.getNamePath());
        if (byPath != null) {
            return byPath;
        }
        String leaf = cat.getLeafName();
        if (!StringUtils.hasText(leaf)) {
            return findOrCreateCategory(IMPORTED_ROOT, 0, "L1", null);
        }
        Integer byName = linkageMapper.findCjCategoryIdByName(leaf);
        if (byName != null) {
            return byName;
        }
        return createCategory(leaf, findOrCreateCategory(IMPORTED_ROOT, 0, "L1", null), "L2", cat.getCjCategoryId());
    }

    /**
     * The {@code spring.cjdropship.category-mapping} override ("<cj path segment>=<native category
     * id>" entries): lets ops pin a CJ path onto an existing NATIVE category instead of the CJ
     * mirror. Returns null when no segment matches (the common case).
     */
    private Integer configOverrideCategoryId(List<String> namePath) {
        List<String> mapping = config.getCategoryMapping();
        if (namePath == null || namePath.isEmpty() || mapping == null || mapping.isEmpty()) {
            return null;
        }
        Map<String, Integer> normalized = new HashMap<>();
        for (String entry : mapping) {
            if (entry == null) {
                continue;
            }
            int eq = entry.lastIndexOf('=');
            if (eq <= 0 || eq == entry.length() - 1) {
                continue;
            }
            try {
                normalized.put(entry.substring(0, eq).trim().toLowerCase(Locale.ROOT),
                        Integer.valueOf(entry.substring(eq + 1).trim()));
            } catch (NumberFormatException ignore) {
                // skip malformed entry
            }
        }
        for (String segment : namePath) {
            if (segment == null) {
                continue;
            }
            Integer id = normalized.get(segment.trim().toLowerCase(Locale.ROOT));
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    private Integer findOrCreateCategory(String name, int pid, String level, String cjCategoryId) {
        Integer existing = linkageMapper.findCjCategoryIdByName(name);
        return existing != null ? existing : createCategory(name, pid, level, cjCategoryId);
    }

    private Integer createCategory(String name, int pid, String level, String cjCategoryId) {
        LocalDateTime now = LocalDateTime.now();
        LitemallCategory category = new LitemallCategory();
        category.setName(trim(name, CATEGORY_NAME_MAX));
        category.setKeywords(""); // NOT NULL in schema
        category.setDesc(trim(name, VARCHAR_MAX));
        category.setPid(pid);
        category.setIconUrl("");
        category.setPicUrl("");
        category.setLevel(level);
        category.setSortOrder((byte) 50);
        category.setSource(SOURCE_CJ);
        category.setCjCategoryId(cjCategoryId);
        category.setAddTime(now);
        category.setUpdateTime(now);
        category.setDeleted(Boolean.FALSE);
        categoryMapper.insertSelective(category);
        return category.getId();
    }

    /**
     * Wave 25: first provider with an attribution for this snapshot wins; each provider's rows are
     * upserted keyed {@code (source, external_id)} with the curation gate down
     * ({@code display_enabled=0}). No provider match falls back to the legacy name-keyed path
     * (dead for CJ — list sync lands {@code brand=null} — but kept for non-provider sources).
     */
    Integer resolveAttributedBrandId(LitemallCjProduct row, NativeGoodsAggregate.BrandRef legacyBrand) {
        for (AttributionProvider provider : attributionProviders) {
            AttributionProvider.Attribution att = provider.resolve(row);
            // usableIdentityField (not just hasText): defence in depth for future providers — a
            // junk external id ("{}") must never key a shared brand row.
            if (att != null && AttributionProvider.usableIdentityField(att.externalId())) {
                Integer id = upsertProviderBrand(provider.source(), att);
                if (id != null) {
                    return id;
                }
            }
        }
        return resolveBrandId(legacyBrand);
    }

    /**
     * Find-or-create a provider-owned brand/store row. An existing row is returned AS IS (name
     * untouched — admin curation is permanent), resurrecting it if soft-deleted. Creation is
     * race-safe: a concurrent insert loses on {@code uk_brand_source_external} and re-reads.
     */
    Integer upsertProviderBrand(String source, AttributionProvider.Attribution att) {
        String externalId = trim(att.externalId().trim(), 63);
        LitemallBrand existing = linkageMapper.findBrandBySourceAndExternalId(source, externalId);
        if (existing != null) {
            if (Boolean.TRUE.equals(existing.getDeleted())) {
                LitemallBrand revive = new LitemallBrand();
                revive.setId(existing.getId());
                revive.setDeleted(Boolean.FALSE);
                revive.setUpdateTime(LocalDateTime.now());
                brandMapper.updateByPrimaryKeySelective(revive);
            }
            return existing.getId();
        }
        LocalDateTime now = LocalDateTime.now();
        LitemallBrand b = new LitemallBrand();
        b.setName(trim(att.name(), VARCHAR_MAX));
        b.setDesc(""); // NOT NULL in schema
        b.setPicUrl(att.logo() != null ? trim(att.logo(), VARCHAR_MAX) : "");
        b.setSortOrder((byte) 50);
        b.setFloorPrice(BigDecimal.ZERO);
        b.setSource(source);
        b.setExternalId(externalId);
        b.setKind(att.kind());
        b.setDisplayEnabled(Boolean.FALSE); // curation gate: raw provider names never render
        b.setAddTime(now);
        b.setUpdateTime(now);
        b.setDeleted(Boolean.FALSE);
        try {
            brandMapper.insertSelective(b);
            return b.getId();
        } catch (DuplicateKeyException race) {
            LitemallBrand winner = linkageMapper.findBrandBySourceAndExternalId(source, externalId);
            return winner != null ? winner.getId() : null;
        }
    }

    private Integer resolveBrandId(NativeGoodsAggregate.BrandRef brand) {
        if (brand == null || !StringUtils.hasText(brand.getName())) {
            return null;
        }
        Integer existing = linkageMapper.findCjBrandIdByName(brand.getName());
        if (existing != null) {
            return existing;
        }
        LocalDateTime now = LocalDateTime.now();
        LitemallBrand b = new LitemallBrand();
        b.setName(trim(brand.getName(), VARCHAR_MAX));
        b.setDesc(""); // NOT NULL in schema
        b.setPicUrl(""); // NOT NULL in schema
        b.setSortOrder((byte) 50);
        b.setFloorPrice(BigDecimal.ZERO);
        b.setSource(SOURCE_CJ);
        b.setAddTime(now);
        b.setUpdateTime(now);
        b.setDeleted(Boolean.FALSE);
        brandMapper.insertSelective(b);
        return b.getId();
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** Outcome of a promotion batch. */
    public record PromoteResult(int promoted, int failed, int total) {
    }
}
