package org.linlinjava.litemall.order.interfaces.dtos.aftersale;

import org.linlinjava.litemall.order.domain.model.agregates.LitemallAftersaleAggregate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Flat aftersale view for the customer and admin SPAs (prices as numbers, dates as strings). */
public class AftersaleDtoResponse {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Integer id;
    private String aftersaleSn;
    private Integer orderId;
    private Integer userId;
    private Short type;
    private String reason;
    private BigDecimal amount;
    private String[] pictures;
    private String comment;
    private Short status;
    private String statusText;
    private String handleTime;
    private String addTime;

    public static AftersaleDtoResponse fromDomain(LitemallAftersaleAggregate aftersale) {
        AftersaleDtoResponse dto = new AftersaleDtoResponse();
        dto.id = aftersale.getId();
        dto.aftersaleSn = aftersale.getAftersaleSn();
        dto.orderId = aftersale.getOrderId() == null ? null : aftersale.getOrderId().getId();
        dto.userId = aftersale.getUserId() == null ? null : aftersale.getUserId().getId();
        dto.type = aftersale.getType();
        dto.reason = aftersale.getReason();
        dto.amount = aftersale.getAmount() == null ? null : aftersale.getAmount().getAmount();
        dto.pictures = aftersale.getPictures();
        dto.comment = aftersale.getComment();
        dto.status = aftersale.getStatus() == null ? null : aftersale.getStatus().getCode();
        dto.statusText = aftersale.getStatus() == null ? null : aftersale.getStatus().getDisplayName();
        dto.handleTime = format(aftersale.getHandleTime());
        dto.addTime = format(aftersale.getAddTime());
        return dto;
    }

    private static String format(LocalDateTime time) {
        return time == null ? null : DT.format(time);
    }

    public Integer getId() {
        return id;
    }

    public String getAftersaleSn() {
        return aftersaleSn;
    }

    public Integer getOrderId() {
        return orderId;
    }

    public Integer getUserId() {
        return userId;
    }

    public Short getType() {
        return type;
    }

    public String getReason() {
        return reason;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String[] getPictures() {
        return pictures;
    }

    public String getComment() {
        return comment;
    }

    public Short getStatus() {
        return status;
    }

    public String getStatusText() {
        return statusText;
    }

    public String getHandleTime() {
        return handleTime;
    }

    public String getAddTime() {
        return addTime;
    }
}
