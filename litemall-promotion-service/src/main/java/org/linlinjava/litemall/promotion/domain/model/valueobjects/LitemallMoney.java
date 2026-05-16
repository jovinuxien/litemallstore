package org.linlinjava.litemall.promotion.domain.model.valueobjects;

import lombok.Getter;

import java.math.BigDecimal;
import java.util.Objects;

@Getter
public class LitemallMoney {

    private final BigDecimal amount;

    public LitemallMoney(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Money amount must be a non-negative value.");
        }
        this.amount = amount;
    }

    public LitemallMoney add(LitemallMoney other) {
        Objects.requireNonNull(other, "Cannot add null money");
        return new LitemallMoney(this.amount.add(other.amount));
    }

    public LitemallMoney subtract(LitemallMoney other) {
        Objects.requireNonNull(other, "Cannot subtract null money");
        BigDecimal result = this.amount.subtract(other.amount);
        if (result.compareTo(BigDecimal.ZERO) < 0) {
            return new LitemallMoney(BigDecimal.ZERO);
        }
        return new LitemallMoney(result);
    }

    public int compareTo(LitemallMoney other) {
        Objects.requireNonNull(other, "Cannot compare with null money");
        return this.amount.compareTo(other.amount);
    }

    public boolean isLessThanOrEqualTo(LitemallMoney other) {
        return this.compareTo(other) <= 0;
    }

    public boolean isGreaterThan(LitemallMoney other) {
        return this.compareTo(other) > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LitemallMoney that = (LitemallMoney) o;
        return Objects.equals(amount, that.amount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount);
    }

    @Override
    public String toString() {
        return "LitemallMoney{amount=" + amount + "}";
    }
}
