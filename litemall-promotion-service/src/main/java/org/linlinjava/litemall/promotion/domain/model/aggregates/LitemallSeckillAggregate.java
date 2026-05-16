package org.linlinjava.litemall.promotion.domain.model.aggregates;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallSeckillId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallSeckillStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@Setter
@Builder
public class LitemallSeckillAggregate {

    private LitemallSeckillId seckillId;
    private Integer goodsId;
    private String goodsName;
    private LitemallMoney price;
    private Integer stock;
    private Integer sales;
    private Integer quota;
    private Byte seckillTime;
    private LitemallSeckillStatus status;
    private LocalDateTime startTime;
    private LocalDateTime stopTime;

    @Builder.Default
    private List<LitemallDomainEvent> domainEvents = new ArrayList<>();

    public boolean isActive() {
        return LitemallSeckillStatus.ACTIVE.equals(this.status);
    }

    public boolean isAvailable(int requestedQty) {
        return isActive() && this.stock != null && this.stock >= requestedQty;
    }

    public void reserveStock(int qty) {
        if (!isAvailable(qty)) {
            throw new IllegalStateException("Cannot reserve stock: insufficient stock or seckill inactive.");
        }
        this.stock = this.stock - qty;
        if (this.sales == null) {
            this.sales = 0;
        }
        this.sales = this.sales + qty;
    }

    public boolean canUserPurchase(int alreadyBought) {
        if (quota == null || quota == 0) {
            return true;
        }
        return alreadyBought < quota;
    }

    public void addDomainEvent(LitemallDomainEvent event) {
        this.domainEvents.add(event);
    }

    public List<LitemallDomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void clearDomainEvents() {
        this.domainEvents.clear();
    }
}
