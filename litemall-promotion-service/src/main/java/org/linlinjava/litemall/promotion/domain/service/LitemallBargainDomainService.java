package org.linlinjava.litemall.promotion.domain.service;

import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallBargainAggregate;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallBargainUserAggregate;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;

@Service
public class LitemallBargainDomainService {

    private final Random random = new Random();

    /**
     * Calculate a random help amount between bargainMinPrice and bargainMaxPrice.
     */
    public LitemallMoney calculateHelpAmount(LitemallBargainAggregate bargain) {
        BigDecimal min = bargain.getBargainMinPrice().getAmount();
        BigDecimal max = bargain.getBargainMaxPrice().getAmount();

        if (min.compareTo(max) >= 0) {
            return new LitemallMoney(min);
        }

        BigDecimal range = max.subtract(min);
        BigDecimal randomFraction = BigDecimal.valueOf(random.nextDouble());
        BigDecimal helpAmount = min.add(range.multiply(randomFraction))
                .setScale(2, RoundingMode.HALF_UP);

        return new LitemallMoney(helpAmount);
    }

    /**
     * Check if the bargain target has been reached.
     * Target reached when bargainPrice <= bargainPriceMin.
     */
    public boolean isTargetReached(LitemallBargainUserAggregate user) {
        return user.getBargainPrice().isLessThanOrEqualTo(user.getBargainPriceMin());
    }
}
