package org.linlinjava.litemall.goods.infrastructure.acl.adapter;

import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallGoodsAttribute;
import org.linlinjava.litemall.db.domain.LitemallGoodsProduct;
import org.linlinjava.litemall.db.domain.LitemallGoodsSpecification;

import java.util.ArrayList;
import java.util.List;

/**
 * A CJ Dropshipping product projected onto the native litemall goods model, ready for
 * persistence. Produced by {@link CjProductToNativeAdapter} from an enriched
 * {@code litemall_cj_product} row; consumed by the Phase-3 promotion service.
 *
 * <p>The promotion service is responsible for the parts the adapter cannot know in
 * isolation: resolving {@link #getCategory()} / {@link #getBrand()} to real
 * {@code litemall_category} / {@code litemall_brand} ids, stamping the generated
 * {@code goodsId} onto every child row, and upserting all parts in one transaction.
 *
 * <p>The {@link #getGoods()} row carries {@code source='cj'} + {@code cj_pid}; each
 * {@link #getProducts()} SKU carries its {@code cj_vid} so {@code litemall-order} can
 * replay the variant to CJ {@code createOrder} at checkout. Nothing is lost in the
 * landing.
 */
public class NativeGoodsAggregate {

    private final LitemallGoods goods;
    private final List<LitemallGoodsProduct> products = new ArrayList<>();
    private final List<LitemallGoodsSpecification> specifications = new ArrayList<>();
    private final List<LitemallGoodsAttribute> attributes = new ArrayList<>();
    private CategoryRef category;
    private BrandRef brand;

    public NativeGoodsAggregate(LitemallGoods goods) {
        this.goods = goods;
    }

    public LitemallGoods getGoods() {
        return goods;
    }

    public List<LitemallGoodsProduct> getProducts() {
        return products;
    }

    public List<LitemallGoodsSpecification> getSpecifications() {
        return specifications;
    }

    public List<LitemallGoodsAttribute> getAttributes() {
        return attributes;
    }

    public CategoryRef getCategory() {
        return category;
    }

    public void setCategory(CategoryRef category) {
        this.category = category;
    }

    public BrandRef getBrand() {
        return brand;
    }

    public void setBrand(BrandRef brand) {
        this.brand = brand;
    }

    /**
     * CJ category descriptor. The promotion service finds-or-creates the matching
     * {@code litemall_category} (idempotent on {@code cj_category_id} when present,
     * otherwise on the leaf name) and stamps the resulting id onto the goods row.
     */
    public static final class CategoryRef {
        private final String cjCategoryId; // nullable — CJ category id when known
        private final List<String> namePath; // CJ category names, leaf last (may be empty)

        public CategoryRef(String cjCategoryId, List<String> namePath) {
            this.cjCategoryId = cjCategoryId;
            this.namePath = namePath == null ? List.of() : namePath;
        }

        public String getCjCategoryId() {
            return cjCategoryId;
        }

        public List<String> getNamePath() {
            return namePath;
        }

        public String getLeafName() {
            return namePath.isEmpty() ? null : namePath.get(namePath.size() - 1);
        }
    }

    /** CJ brand / supplier descriptor. Nullable — CJ frequently omits the brand. */
    public static final class BrandRef {
        private final String name;

        public BrandRef(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
