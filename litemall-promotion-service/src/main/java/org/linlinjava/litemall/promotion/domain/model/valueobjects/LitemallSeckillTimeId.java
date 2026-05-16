package org.linlinjava.litemall.promotion.domain.model.valueobjects;

import lombok.Getter;

import java.util.Objects;

@Getter
public class LitemallSeckillTimeId {

    private final Integer id;

    public LitemallSeckillTimeId(Integer id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("SeckillTime ID must be a positive integer.");
        }
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LitemallSeckillTimeId that = (LitemallSeckillTimeId) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "LitemallSeckillTimeId{id=" + id + "}";
    }
}
