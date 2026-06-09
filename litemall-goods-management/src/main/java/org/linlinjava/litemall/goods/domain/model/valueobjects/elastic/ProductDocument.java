package org.linlinjava.litemall.goods.domain.model.valueobjects.elastic;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flat document matching the OCS {@code litemall_index} field set defined in
 * {@code docker-compose/application.indexer-service.yml}. The OCS indexer
 * service owns the Elasticsearch mapping; this DTO is the {@code data}
 * payload of an OCS {@code Document} envelope.
 *
 * <p>Two extension points beyond the nine fixed fields:
 * <ul>
 *   <li>{@link #attributes} — curated {@code litemall_goods_attribute} name→value pairs serialized
 *       as additional flat {@code data} keys ({@code @JsonAnyGetter}); the index config's
 *       {@code dynamic-fields} turns each into a facet with no per-attribute config.</li>
 *   <li>{@link #variants} — per-SKU data (variant_price/stock) carried OUTSIDE {@code data}
 *       ({@code @JsonIgnore}); the OCS adapter lifts it to the document envelope's {@code variants}
 *       array, where OCS indexes variant-level fields and flattens the matched SKU into hits.</li>
 * </ul>
 */
public class ProductDocument {

    @JsonProperty("product_id")
    private String productId;

    @JsonProperty("title")
    private String title;

    @JsonProperty("price")
    private BigDecimal price;

    @JsonProperty("discount_price")
    private BigDecimal discountPrice;

    @JsonProperty("description")
    private String description;

    @JsonProperty("image_url")
    private String imageUrl;

    @JsonProperty("brand")
    private String brand;

    @JsonProperty("category_names")
    private List<String> categoryNames;

    @JsonProperty("category_ids")
    private List<String> categoryIds;

    /** Curated attribute name→value pairs, emitted as extra flat {@code data} keys. */
    private final Map<String, Object> attributes = new LinkedHashMap<>();

    /** Per-SKU variant data maps (e.g. {@code variant_price}, {@code stock}); not part of {@code data}. */
    @JsonIgnore
    private final List<Map<String, Object>> variants = new ArrayList<>();

    public ProductDocument() {
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getDiscountPrice() {
        return discountPrice;
    }

    public void setDiscountPrice(BigDecimal discountPrice) {
        this.discountPrice = discountPrice;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public List<String> getCategoryNames() {
        return categoryNames;
    }

    public void setCategoryNames(List<String> categoryNames) {
        this.categoryNames = categoryNames;
    }

    public List<String> getCategoryIds() {
        return categoryIds;
    }

    public void setCategoryIds(List<String> categoryIds) {
        this.categoryIds = categoryIds;
    }

    /** Serialized as extra top-level {@code data} keys (one per curated attribute). */
    @JsonAnyGetter
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * Add a curated attribute value. Repeated names accumulate into a list so OCS indexes them as a
     * multi-value facet; nulls/blanks are ignored.
     */
    @SuppressWarnings("unchecked")
    public void addAttribute(String name, String value) {
        if (name == null || name.isBlank() || value == null || value.isBlank()) {
            return;
        }
        Object existing = attributes.get(name);
        if (existing == null) {
            attributes.put(name, value);
        } else if (existing instanceof List) {
            ((List<Object>) existing).add(value);
        } else {
            List<Object> list = new ArrayList<>();
            list.add(existing);
            list.add(value);
            attributes.put(name, list);
        }
    }

    public List<Map<String, Object>> getVariants() {
        return variants;
    }

    public void addVariant(Map<String, Object> variant) {
        if (variant != null && !variant.isEmpty()) {
            variants.add(variant);
        }
    }
}
