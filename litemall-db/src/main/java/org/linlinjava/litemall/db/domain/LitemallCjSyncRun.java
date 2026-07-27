package org.linlinjava.litemall.db.domain;

import java.time.LocalDateTime;

/**
 * CJ catalog pipeline run bookkeeping row (table {@code litemall_cj_sync_run}, V45).
 *
 * <p>One row per pipeline phase execution ({@code sync} | {@code enrich} | {@code flow}).
 * Before Wave 12 the only record of a nightly run was a log line; these rows make counts
 * and failures queryable (the Wave-12 inventory flow's wire tap writes them). Plain POJO,
 * hand-maintained, no MyBatis Generator {@code Example} support.
 */
public class LitemallCjSyncRun {

    public static final String PHASE_SYNC = "sync";
    public static final String PHASE_ENRICH = "enrich";
    public static final String PHASE_FLOW = "flow";

    private Integer id;
    private String phase;
    private LocalDateTime startedTime;
    private LocalDateTime finishedTime;
    private Integer upserted;
    private Integer inserted;
    private Integer updated;
    private Integer removed;
    private Boolean complete;
    private String error;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public LocalDateTime getStartedTime() {
        return startedTime;
    }

    public void setStartedTime(LocalDateTime startedTime) {
        this.startedTime = startedTime;
    }

    public LocalDateTime getFinishedTime() {
        return finishedTime;
    }

    public void setFinishedTime(LocalDateTime finishedTime) {
        this.finishedTime = finishedTime;
    }

    public Integer getUpserted() {
        return upserted;
    }

    public void setUpserted(Integer upserted) {
        this.upserted = upserted;
    }

    public Integer getInserted() {
        return inserted;
    }

    public void setInserted(Integer inserted) {
        this.inserted = inserted;
    }

    public Integer getUpdated() {
        return updated;
    }

    public void setUpdated(Integer updated) {
        this.updated = updated;
    }

    public Integer getRemoved() {
        return removed;
    }

    public void setRemoved(Integer removed) {
        this.removed = removed;
    }

    public Boolean getComplete() {
        return complete;
    }

    public void setComplete(Boolean complete) {
        this.complete = complete;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
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
}
