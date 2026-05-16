package org.linlinjava.litemall.promotion.domain.model.aggregates;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallSeckillTimeId;

@Getter
@Setter
@Builder
public class LitemallSeckillTimeAggregate {

    private LitemallSeckillTimeId timeId;
    private Byte hour;
    private Boolean status;

    public boolean isEnabled() {
        return Boolean.TRUE.equals(status);
    }
}
