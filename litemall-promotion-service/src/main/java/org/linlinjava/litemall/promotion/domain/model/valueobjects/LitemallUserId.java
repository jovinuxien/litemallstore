package org.linlinjava.litemall.promotion.domain.model.valueobjects;

import lombok.Getter;

import java.util.Objects;

@Getter
public class LitemallUserId {

    private final Integer id;

    public LitemallUserId(Integer id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("User ID must be a positive integer.");
        }
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LitemallUserId that = (LitemallUserId) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "LitemallUserId{id=" + id + "}";
    }
}
