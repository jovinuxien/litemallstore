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

    /**
     * Origin of the document: {@code "local"} for DB-backed goods or {@code "cj_dropshipping"}
     * for CJ Dropshipping catalog products. Indexed Result+Facet so the customer search can tag
     * each hit and (optionally) filter by origin — but it is metadata/routing only and must NOT
     * split or default-scope the unified result set. CJ hits are otherwise identified purely by
     * their {@code cj_<pid>} {@link #productId}.
     */
    @JsonProperty("source")
    private String source;

    /**
     * Ranking signals (V31), master-level numeric fields the searcher scoring-configuration
     * multiplies into relevance (Relevant Search ch. 7). {@code listedNum} = CJ platform
     * popularity; {@code reviewCount}/{@code rating} = unified review aggregate;
     * {@code createdEpoch} = product creation date (epoch millis) for the recency decay / New
     * Arrivals sort. Absent (null) when a signal is unavailable, so OCS treats it as neutral.
     */
    @JsonProperty("listed_num")
    private Integer listedNum;

    @JsonProperty("review_count")
    private Integer reviewCount;

    @JsonProperty("rating")
    private BigDecimal rating;

    @JsonProperty("created_epoch")
    private Long createdEpoch;

    /**
     * Deal signals derived from the {@code counter_price}/{@code retail_price} split at index
     * time (never per-query — Relevant Search: signals must be precise index-time facts).
     * {@code discountPct} = whole-percent markdown (0 when not discounted); {@code dealFlag} =
     * 1 when the markdown clears the configured threshold, else 0 — the flat filter key the
     * deals surfaces select on ({@code deal_flag=1}).
     */
    @JsonProperty("discount_pct")
    private Integer discountPct;

    @JsonProperty("deal_flag")
    private Integer dealFlag;

    /**
     * Live flash-deal signals (price-swap lifecycle, Phase B). The three QUERYABLE fields ride
     * every document — {@code dealActive} 1/0, {@code dealEndEpoch} (millis; card countdown +
     * "ending soon" sort, {@code DealMath.NO_DEAL_END_EPOCH} when no deal so live deals always
     * sort first), {@code dealUrgency} 0–100 index-time gaussian the searcher multiplies in
     * (OCS SCRIPT_CODE takes no params, so no query-time decay; 0 = uniform ln2p baseline).
     * Always-emit is deliberate: OCS resolves filter/sort/score fields against the index
     * mapping, and a conditionally-emitted field vanishes from a full reindex taken while no
     * deal is live. {@code dealClaimedPct} is Result-only and stays live-deals-only (absent
     * for uncapped deals too).
     */
    @JsonProperty("deal_active")
    private Integer dealActive;

    @JsonProperty("deal_end_epoch")
    private Long dealEndEpoch;

    @JsonProperty("deal_claimed_pct")
    private Integer dealClaimedPct;

    @JsonProperty("deal_urgency")
    private Integer dealUrgency;

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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Integer getListedNum() {
        return listedNum;
    }

    public void setListedNum(Integer listedNum) {
        this.listedNum = listedNum;
    }

    public Integer getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(Integer reviewCount) {
        this.reviewCount = reviewCount;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public void setRating(BigDecimal rating) {
        this.rating = rating;
    }

    public Long getCreatedEpoch() {
        return createdEpoch;
    }

    public void setCreatedEpoch(Long createdEpoch) {
        this.createdEpoch = createdEpoch;
    }

    public Integer getDiscountPct() {
        return discountPct;
    }

    public void setDiscountPct(Integer discountPct) {
        this.discountPct = discountPct;
    }

    public Integer getDealFlag() {
        return dealFlag;
    }

    public void setDealFlag(Integer dealFlag) {
        this.dealFlag = dealFlag;
    }

    public Integer getDealActive() {
        return dealActive;
    }

    public void setDealActive(Integer dealActive) {
        this.dealActive = dealActive;
    }

    public Long getDealEndEpoch() {
        return dealEndEpoch;
    }

    public void setDealEndEpoch(Long dealEndEpoch) {
        this.dealEndEpoch = dealEndEpoch;
    }

    public Integer getDealClaimedPct() {
        return dealClaimedPct;
    }

    public void setDealClaimedPct(Integer dealClaimedPct) {
        this.dealClaimedPct = dealClaimedPct;
    }

    public Integer getDealUrgency() {
        return dealUrgency;
    }

    public void setDealUrgency(Integer dealUrgency) {
        this.dealUrgency = dealUrgency;
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
