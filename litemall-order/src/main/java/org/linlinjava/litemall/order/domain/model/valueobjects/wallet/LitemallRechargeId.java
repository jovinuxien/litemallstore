package org.linlinjava.litemall.order.domain.model.valueobjects.wallet;

import lombok.Getter;

@Getter
public class LitemallRechargeId {

    private final Integer id;

    public LitemallRechargeId(Integer id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Recharge ID must be positive");
        }
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LitemallRechargeId that = (LitemallRechargeId) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "LitemallRechargeId{id=" + id + '}';
    }
}