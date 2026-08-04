package org.linlinjava.litemall.goods.application.tracking;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * One client-submitted event in a {@code POST /srv/track/collect} batch.
 *
 * <p>{@code occurredAt} is EPOCH MILLIS by contract (the module's ObjectMapper
 * serializes {@code LocalDateTime} as arrays — epoch millis sidesteps that on both
 * directions). {@code @JsonIgnoreProperties} is required: the module's raw
 * ObjectMapper bean keeps Jackson's fail-on-unknown default, and an unexpected
 * client field must not 400 the whole batch.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TrackEventDto {

    private String eventId;
    private String type;
    private Long occurredAt;
    private Integer goodsId;
    private Integer productId;
    private Integer categoryId;
    private String searchQuery;
    private Integer position;
    private String pageType;
    private Map<String, Object> payload;

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Long occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Integer getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(Integer goodsId) {
        this.goodsId = goodsId;
    }

    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    public String getPageType() {
        return pageType;
    }

    public void setPageType(String pageType) {
        this.pageType = pageType;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }
}
