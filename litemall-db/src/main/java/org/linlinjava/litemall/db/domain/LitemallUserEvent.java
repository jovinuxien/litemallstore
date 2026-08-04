package org.linlinjava.litemall.db.domain;

import java.time.LocalDateTime;

/**
 * One row of the append-only first-party behavioral event log
 * (table {@code litemall_user_event}, V49; UNIQUE event_id).
 *
 * <p>Contract: {@code doc/behavioral-events.md}. Rows are immutable — there is no
 * deleted column and no update path; identity resolves at query time via
 * {@code litemall_visitor_identity}. Plain POJO, hand-maintained, no MyBatis
 * Generator {@code Example} support.
 */
public class LitemallUserEvent {

    /** origin: emitted by the SPA through POST /srv/track/collect. */
    public static final int ORIGIN_CLIENT = 1;
    /** origin: emitted by a backend service (order paid/refund listeners). */
    public static final int ORIGIN_SERVER = 2;

    public static final String TYPE_PAGE_VIEW = "page_view";
    public static final String TYPE_VIEW_ITEM = "view_item";
    public static final String TYPE_VIEW_CATEGORY = "view_category";
    public static final String TYPE_SEARCH = "search";
    public static final String TYPE_CLICK_RESULT = "click_result";
    public static final String TYPE_ADD_TO_CART = "add_to_cart";
    public static final String TYPE_REMOVE_FROM_CART = "remove_from_cart";
    public static final String TYPE_BEGIN_CHECKOUT = "begin_checkout";
    public static final String TYPE_PURCHASE = "purchase";
    public static final String TYPE_REFUND = "refund";

    private Long id;
    private String eventId;
    private String visitorId;
    private String sessionId;
    private Integer userId;
    private String eventType;
    private Integer origin;
    private LocalDateTime occurredAt;
    private LocalDateTime receivedAt;
    private Integer goodsId;
    private Integer productId;
    private Integer categoryId;
    private String searchQuery;
    private Integer position;
    private String pageType;
    private String locale;
    private String countryCode;
    private String deviceType;
    private String payload;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getVisitorId() {
        return visitorId;
    }

    public void setVisitorId(String visitorId) {
        this.visitorId = visitorId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Integer getOrigin() {
        return origin;
    }

    public void setOrigin(Integer origin) {
        this.origin = origin;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
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

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}
