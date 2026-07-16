package org.linlinjava.litemall.db.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One commission ledger entry (table {@code litemall_user_brokerage_record},
 * created in V7 but unmapped until Wave 5's brokerage engine).
 *
 * <p>Hand-written (not MyBatis-Generator output) and co-located with the generated
 * domains, like {@link LitemallCjSourcingRequest}. Lifecycle (income rows,
 * {@code pm=1}): FROZEN(0) at pay → VALID(1) once the freeze window elapses and the
 * amount is credited into {@code litemall_user.brokerage_price}, or INVALID(-1) when
 * an approved aftersale claws the commission back before maturity. Withdrawal debits
 * are {@code pm=0} rows written already-VALID ({@code link_type='extract'}).
 */
public class LitemallUserBrokerageRecord {

    /** Income (commission earned). */
    public static final Boolean PM_INCOME = Boolean.TRUE;
    /** Expenditure (withdrawal debit). */
    public static final Boolean PM_EXPENDITURE = Boolean.FALSE;

    public static final byte STATUS_FROZEN = 0;
    public static final byte STATUS_VALID = 1;
    public static final byte STATUS_INVALID = -1;

    public static final String LINK_TYPE_ORDER = "order";
    public static final String LINK_TYPE_EXTRACT = "extract";

    private Integer id;
    /** Commission owner (the promoter), litemall_user.id. */
    private Integer userId;
    /** orderSn for {@code link_type='order'}; litemall_user_extract.id for 'extract'. */
    private String linkId;
    /** {@link #LINK_TYPE_ORDER} or {@link #LINK_TYPE_EXTRACT}. */
    private String linkType;
    /** 0 = expenditure, 1 = income (tinyint(1) → Boolean under the global type handler). */
    private Boolean pm;
    private String title;
    /** Commission amount, always positive; pm carries the direction. */
    private BigDecimal price;
    /** Snapshot of litemall_user.brokerage_price at write time. */
    private BigDecimal balance;
    private String mark;
    /** 0 frozen, 1 valid, -1 invalid. */
    private Byte status;
    private LocalDateTime freezeTime;
    /** When the frozen amount becomes creditable (freeze_time + freeze days). */
    private LocalDateTime unfreezeTime;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public String getLinkId() { return linkId; }
    public void setLinkId(String linkId) { this.linkId = linkId; }

    public String getLinkType() { return linkType; }
    public void setLinkType(String linkType) { this.linkType = linkType; }

    public Boolean getPm() { return pm; }
    public void setPm(Boolean pm) { this.pm = pm; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public String getMark() { return mark; }
    public void setMark(String mark) { this.mark = mark; }

    public Byte getStatus() { return status; }
    public void setStatus(Byte status) { this.status = status; }

    public LocalDateTime getFreezeTime() { return freezeTime; }
    public void setFreezeTime(LocalDateTime freezeTime) { this.freezeTime = freezeTime; }

    public LocalDateTime getUnfreezeTime() { return unfreezeTime; }
    public void setUnfreezeTime(LocalDateTime unfreezeTime) { this.unfreezeTime = unfreezeTime; }

    public LocalDateTime getAddTime() { return addTime; }
    public void setAddTime(LocalDateTime addTime) { this.addTime = addTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
}
