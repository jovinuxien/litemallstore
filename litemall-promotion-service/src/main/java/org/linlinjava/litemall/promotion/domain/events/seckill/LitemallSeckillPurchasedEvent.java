package org.linlinjava.litemall.promotion.domain.events.seckill;

import org.linlinjava.litemall.core.events.LitemallDomainEvent;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallSeckillId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;

public class LitemallSeckillPurchasedEvent extends LitemallDomainEvent {

    private final LitemallSeckillId seckillId;
    private final LitemallUserId userId;
    private final Integer quantity;
    private final LitemallMoney price;

    public LitemallSeckillPurchasedEvent(LitemallSeckillId seckillId, LitemallUserId userId,
                                          Integer quantity, LitemallMoney price) {
        super("SECKILL_PURCHASED");
        this.seckillId = seckillId;
        this.userId = userId;
        this.quantity = quantity;
        this.price = price;
    }

    public LitemallSeckillId getSeckillId() { return seckillId; }
    public LitemallUserId getUserId() { return userId; }
    public Integer getQuantity() { return quantity; }
    public LitemallMoney getPrice() { return price; }
}
