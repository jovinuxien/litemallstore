package org.linlinjava.litemall.order.domain.model.valueobjects.wallet;

import lombok.Getter;

@Getter
public class LitemallExtractId {

    private final Integer id;

    public LitemallExtractId(Integer id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Extract ID must be positive");
        }
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LitemallExtractId that = (LitemallExtractId) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "LitemallExtractId{id=" + id + '}';
    }
}