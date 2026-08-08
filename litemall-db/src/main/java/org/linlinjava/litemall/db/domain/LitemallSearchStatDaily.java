package org.linlinjava.litemall.db.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Per-day per-keyword search demand rollup
 * (table {@code litemall_search_stat_daily}, V57; UNIQUE day + keyword).
 *
 * <p>Written by the Wave-22 nightly search-stats rollup over the two demand
 * sources: {@code litemall_search_history} (logged-in searches; zero-result
 * flag via {@code result_count}) and the Phase-0 behavioral log
 * {@code litemall_user_event} (anonymous consented {@code search} events +
 * {@code click_result} clicks). Counts are recomputed absolutely per
 * (day, keyword) — the upsert overwrites, so re-running a day is idempotent.
 * Keywords are stored normalized (trim + lowercase, capped at 127).
 * Aggregate-only by design: no visitor/user identities ever land here.
 * Plain POJO, hand-maintained, no MyBatis Generator {@code Example} support.
 */
public class LitemallSearchStatDaily {

    private Integer id;
    private LocalDate day;
    private String keyword;
    private Integer searches;
    private Integer zeroResults;
    private Integer clicks;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public LocalDate getDay() {
        return day;
    }

    public void setDay(LocalDate day) {
        this.day = day;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Integer getSearches() {
        return searches;
    }

    public void setSearches(Integer searches) {
        this.searches = searches;
    }

    public Integer getZeroResults() {
        return zeroResults;
    }

    public void setZeroResults(Integer zeroResults) {
        this.zeroResults = zeroResults;
    }

    public Integer getClicks() {
        return clicks;
    }

    public void setClicks(Integer clicks) {
        this.clicks = clicks;
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

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }
}
