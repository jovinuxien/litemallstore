package org.linlinjava.litemall.db.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One season's merchandising rule ({@code litemall_season_rule}, V64; UNIQUE season_key).
 *
 * <p>A season is DATA, not code: its terms, boosted categories, price band and weights all live
 * here, so adding "Black Friday" or "Garden season" is an INSERT rather than an edit to the
 * scorer. The four northern-hemisphere seasons are seeded by the migration — correct for a
 * DE/FR/DK/SE market — but nothing caps the table at four.
 *
 * <p>{@code windowStartMd}/{@code windowEndMd} are {@code MM-DD} and recur yearly; a window may
 * wrap the year end (winter), which {@link #coversMonthDay} handles.
 *
 * <p>Plain POJO, hand-maintained, no MyBatis Generator {@code Example} support.
 */
public class LitemallSeasonRule {

    public static final String KEY_AUTUMN = "autumn";
    public static final String KEY_WINTER = "winter";
    public static final String KEY_SPRING = "spring";
    public static final String KEY_SUMMER = "summer";

    private Integer id;
    private String seasonKey;
    private String name;
    private String windowStartMd;
    private String windowEndMd;
    private String terms;
    private String categoryIds;
    private BigDecimal priceMin;
    private BigDecimal priceMax;
    private String weights;
    private Boolean enabled;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getSeasonKey() {
        return seasonKey;
    }

    public void setSeasonKey(String seasonKey) {
        this.seasonKey = seasonKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWindowStartMd() {
        return windowStartMd;
    }

    public void setWindowStartMd(String windowStartMd) {
        this.windowStartMd = windowStartMd;
    }

    public String getWindowEndMd() {
        return windowEndMd;
    }

    public void setWindowEndMd(String windowEndMd) {
        this.windowEndMd = windowEndMd;
    }

    public String getTerms() {
        return terms;
    }

    public void setTerms(String terms) {
        this.terms = terms;
    }

    public String getCategoryIds() {
        return categoryIds;
    }

    public void setCategoryIds(String categoryIds) {
        this.categoryIds = categoryIds;
    }

    public BigDecimal getPriceMin() {
        return priceMin;
    }

    public void setPriceMin(BigDecimal priceMin) {
        this.priceMin = priceMin;
    }

    public BigDecimal getPriceMax() {
        return priceMax;
    }

    public void setPriceMax(BigDecimal priceMax) {
        this.priceMax = priceMax;
    }

    public String getWeights() {
        return weights;
    }

    public void setWeights(String weights) {
        this.weights = weights;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getAddTime() {
        return addTime;
    }

    public void setAddTime(LocalDateTime addTime) {
        this.addTime = addTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public Boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    /**
     * Whether this season's window covers the given {@code MM-DD}.
     *
     * <p>Windows that wrap the year end (winter, 12-01 → 02-28) are the reason this is not a
     * plain range check: for those, a date matches when it is at or after the start OR at or
     * before the end.
     *
     * @return false when either bound is missing — an unusable window never claims a date.
     */
    public boolean coversMonthDay(String monthDay) {
        if (monthDay == null || windowStartMd == null || windowEndMd == null) {
            return false;
        }
        if (windowStartMd.compareTo(windowEndMd) <= 0) {
            return monthDay.compareTo(windowStartMd) >= 0 && monthDay.compareTo(windowEndMd) <= 0;
        }
        return monthDay.compareTo(windowStartMd) >= 0 || monthDay.compareTo(windowEndMd) <= 0;
    }
}
