package org.linlinjava.litemall.promotion.domain.model.aggregates;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallBargainId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallBargainStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@Setter
@Builder
public class LitemallBargainAggregate {

    private LitemallBargainId bargainId;
    private Integer goodsId;
    private String title;
    private LitemallMoney price;
    private LitemallMoney minPrice;
    private LitemallMoney bargainMaxPrice;
    private LitemallMoney bargainMinPrice;
    private Integer bargainNum;
    private Integer peopleNum;
    private Integer stock;
    private LitemallBargainStatus status;
    private LocalDateTime startTime;
    private LocalDateTime stopTime;

    @Builder.Default
    private List<LitemallDomainEvent> domainEvents = new ArrayList<>();

    public boolean isActive() {
        return LitemallBargainStatus.ACTIVE.equals(this.status);
    }

    public boolean isAvailable() {
        return isActive() && this.stock != null && this.stock > 0;
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
