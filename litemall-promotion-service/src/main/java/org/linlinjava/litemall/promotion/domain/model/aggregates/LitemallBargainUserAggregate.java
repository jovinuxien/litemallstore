package org.linlinjava.litemall.promotion.domain.model.aggregates;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallBargainId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallBargainUserId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallBargainUserStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@Setter
@Builder
public class LitemallBargainUserAggregate {

    private LitemallBargainUserId bargainUserId;
    private LitemallUserId userId;
    private LitemallBargainId bargainId;
    private LitemallMoney bargainPriceMin;
    private LitemallMoney bargainPrice;
    private LitemallBargainUserStatus status;

    @Builder.Default
    private List<LitemallDomainEvent> domainEvents = new ArrayList<>();

    public void applyHelp(LitemallMoney helpAmount) {
        if (!LitemallBargainUserStatus.ONGOING.equals(this.status)) {
            throw new IllegalStateException("Cannot apply help: bargain session is not ongoing.");
        }
        this.bargainPrice = this.bargainPrice.subtract(helpAmount);
        if (this.bargainPrice.isLessThanOrEqualTo(this.bargainPriceMin)) {
            this.bargainPrice = this.bargainPriceMin;
            this.status = LitemallBargainUserStatus.SUCCESS;
        }
    }

    public boolean isSuccessful() {
        return LitemallBargainUserStatus.SUCCESS.equals(this.status);
    }

    public boolean isExpired(LocalDateTime expiry) {
        return expiry != null && LocalDateTime.now().isAfter(expiry);
    }

    public boolean isOngoing() {
        return LitemallBargainUserStatus.ONGOING.equals(this.status);
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
