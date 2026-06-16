package org.linlinjava.litemall.order.domain.model.valueobjects.wallet;

import lombok.Getter;

@Getter
public class LitemallBillId {

    private final Integer id;

    public LitemallBillId(Integer id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Bill ID must be positive");
        }
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LitemallBillId that = (LitemallBillId) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "LitemallBillId{id=" + id + '}';
    }
}