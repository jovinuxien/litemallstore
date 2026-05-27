package org.linlinjava.litemall.goods.infrastructure.acl.ocs;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * Flat DTO matching the OCS {@code litemall_index} schema declared in
 * {@code docker-compose/application.indexer-service.yml}. The 9 fields below
 * are what the OCS indexer accepts; the OCS searcher returns documents in
 * the same shape.
 *
 * <p>Field names use snake_case (matching OCS) via {@code @JsonProperty}.
 * Mapping from litemall goods (catalog table) is done in the indexer client.
 */
public class OcsProductDocument {

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

    public OcsProductDocument() {}

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getDiscountPrice() { return discountPrice; }
    public void setDiscountPrice(BigDecimal discountPrice) { this.discountPrice = discountPrice; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public List<String> getCategoryNames() { return categoryNames; }
    public void setCategoryNames(List<String> categoryNames) { this.categoryNames = categoryNames; }

    public List<String> getCategoryIds() { return categoryIds; }
    public void setCategoryIds(List<String> categoryIds) { this.categoryIds = categoryIds; }
}
