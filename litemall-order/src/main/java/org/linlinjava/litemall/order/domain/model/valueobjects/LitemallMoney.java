package org.linlinjava.litemall.order.domain.model.valueobjects;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;

import lombok.Getter;

import java.math.BigDecimal;
import java.util.Objects;


@Getter
public class LitemallMoney {

    private final BigDecimal amount;

    public LitemallMoney(BigDecimal amount) {
        if(amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Money amount must be a non-negative value.");
        }
        this.amount = amount;
    }

    public LitemallMoney add(LitemallMoney other) {
        return new LitemallMoney(this.amount.add(other.amount));
    }

    public LitemallMoney subtract(LitemallMoney other) {
        BigDecimal result = this.amount.subtract(other.amount);
        if (result.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Insufficient balance");
        }
        return new LitemallMoney(result);
    }

    public boolean isGreaterThanOrEqual(LitemallMoney other) {
        return this.amount.compareTo(other.amount) >= 0;
    }

    @Override
    public String toString() {
        return amount.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LitemallMoney that = (LitemallMoney) o;
        return amount.compareTo(that.amount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(amount.stripTrailingZeros());
    }
}
