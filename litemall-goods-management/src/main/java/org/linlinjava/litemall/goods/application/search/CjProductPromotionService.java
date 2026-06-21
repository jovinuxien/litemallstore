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
import org.linlinjava.litemall.goods.infrastructure.acl.adapter.CjProductToNativeAdapter;
import org.linlinjava.litemall.goods.infrastructure.acl.adapter.NativeGoodsAggregate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
    private final CjProductToNativeAdapter adapter;
    private final TransactionTemplate txTemplate;

    public CjProductPromotionService(LitemallCjLinkageMapper linkageMapper,
                                     LitemallGoodsMapper goodsMapper,
                                     LitemallGoodsProductMapper productMapper,
                                     LitemallGoodsAttributeMapper attributeMapper,
                                     LitemallGoodsSpecificationMapper specificationMapper,
                                     LitemallCategoryMapper categoryMapper,
                                     LitemallBrandMapper brandMapper,
                                     CjProductToNativeAdapter adapter,
                                     PlatformTransactionManager transactionManager) {
        this.linkageMapper = linkageMapper;
        this.goodsMapper = goodsMapper;
        this.productMapper = productMapper;
        this.attributeMapper = attributeMapper;
        this.specificationMapper = specificationMapper;
        this.categoryMapper = categoryMapper;
        this.brandMapper = brandMapper;
        this.adapter = adapter;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Promote up to {@code limit} enriched CJ rows into native goods. Each row runs in its own
     * transaction; a failure is logged and counted, never aborting the batch.
     */
    public PromoteResult promoteBatch(int limit) {
        List<LitemallCjProduct> rows = linkageMapper.selectEnriched(limit);
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
    public int reconcile(Set<String> liveCjPids) {
        return txTemplate.execute(status -> {
            int removed = 0;
            for (LitemallGoods ref : linkageMapper.findCjGoodsRefs()) {
                if (ref.getCjPid() != null && !liveCjPids.contains(ref.getCjPid())) {
                    goodsMapper.logicalDeleteByPrimaryKey(ref.getId());
                    LitemallGoodsProductExample ex = new LitemallGoodsProductExample();
                    ex.createCriteria().andGoodsIdEqualTo(ref.getId()).andDeletedEqualTo(false);
                    for (LitemallGoodsProduct p : productMapper.selectByExample(ex)) {
                        productMapper.logicalDeleteByPrimaryKey(p.getId());
                    }
                    removed++;
                }
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
        Integer brandId = resolveBrandId(aggregate.getBrand());
        goods.setBrandId(brandId != null ? brandId : 0);

        Integer existingId = linkageMapper.findGoodsIdByCjPid(goods.getCjPid());
        if (existingId != null) {
            goods.setId(existingId);
            goods.setAddTime(null); // preserve the original creation time on update
            goodsMapper.updateByPrimaryKeySelective(goods);
        } else {
            goodsMapper.insertSelective(goods); // selectKey stamps goods.id
        }
        Integer goodsId = goods.getId();

        upsertProducts(goodsId, aggregate.getProducts());
        regenerateSpecifications(goodsId, aggregate.getSpecifications());
        regenerateAttributes(goodsId, aggregate.getAttributes());
        return goodsId;
    }

    /** Diff SKUs by cj_vid: matched -> update (stable id), new -> insert, vanished -> soft-delete. */
    private void upsertProducts(Integer goodsId, List<LitemallGoodsProduct> incoming) {
        LitemallGoodsProductExample ex = new LitemallGoodsProductExample();
        ex.createCriteria().andGoodsIdEqualTo(goodsId).andDeletedEqualTo(false);
        List<LitemallGoodsProduct> existing = productMapper.selectByExample(ex);

        Map<String, LitemallGoodsProduct> byVid = new HashMap<>();
        for (LitemallGoodsProduct p : existing) {
            byVid.put(p.getCjVid(), p); // null key allowed (synthetic no-variant SKU)
        }

        Set<String> incomingVids = new HashSet<>();
        boolean incomingHasNullVid = false;
        for (LitemallGoodsProduct in : incoming) {
            in.setGoodsId(goodsId);
            if (in.getCjVid() == null) {
                incomingHasNullVid = true;
            } else {
                incomingVids.add(in.getCjVid());
            }
            LitemallGoodsProduct match = byVid.get(in.getCjVid());
            if (match != null) {
                in.setId(match.getId());
                in.setAddTime(null); // keep original creation time
                productMapper.updateByPrimaryKeySelective(in);
            } else {
                productMapper.insertSelective(in);
            }
        }
        for (LitemallGoodsProduct p : existing) {
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
     * Resolve (find-or-create) the litemall_category for a CJ category descriptor. Idempotent on
     * cj_category_id when present, else on the leaf name; the resulting leaf hangs under a single
     * "Imported" L1 root so the storefront channel nav stays clean.
     */
    private Integer resolveCategoryId(NativeGoodsAggregate.CategoryRef cat) {
        Integer rootId = findOrCreateCategory(IMPORTED_ROOT, 0, "L1", null);
        if (cat == null) {
            return rootId;
        }
        if (StringUtils.hasText(cat.getCjCategoryId())) {
            Integer byCjId = linkageMapper.findCjCategoryIdByCjId(cat.getCjCategoryId());
            if (byCjId != null) {
                return byCjId;
            }
        }
        String leaf = cat.getLeafName();
        if (!StringUtils.hasText(leaf)) {
            return rootId;
        }
        Integer byName = linkageMapper.findCjCategoryIdByName(leaf);
        if (byName != null) {
            return byName;
        }
        return createCategory(leaf, rootId, "L2", cat.getCjCategoryId());
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
