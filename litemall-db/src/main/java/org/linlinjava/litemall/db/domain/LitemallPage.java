package org.linlinjava.litemall.db.domain;

import java.time.LocalDateTime;

/**
 * DIY page ({@code litemall_page}, V36). Hand-written slim domain
 * (CjSourcingRequest pattern) — NOT MyBatis-Generator output, no Example class.
 *
 * <p>{@code config} holds the palette-v1 JSON (validated + &le;64KB, enforced by
 * goods-management's PageConfigValidator — see
 * litemall-goods-management/docs/spec-page-palette-v1.md, NORMATIVE).
 *
 * <p>The DB additionally carries a STORED generated column
 * {@code active_home_slot} (NOT mapped here — never insert/update it) whose
 * UNIQUE index enforces at most ONE active home page in schema; a concurrent
 * double-activation surfaces as a duplicate-key error.
 */
public class LitemallPage {

    public static final String POSITION_HOME = "home";
    public static final String POSITION_CUSTOM = "custom";
    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_ACTIVE = "active";

    private Integer id;
    /** Admin-facing label. */
    private String name;
    /** home | custom. */
    private String position;
    /** Palette v1 JSON (validated, &le;64KB UTF-8). */
    private String config;
    /** draft | active. */
    private String status;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getConfig() {
        return config;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
