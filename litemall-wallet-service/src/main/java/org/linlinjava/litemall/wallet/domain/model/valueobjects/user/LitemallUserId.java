package org.linlinjava.litemall.wallet.domain.model.valueobjects.user;

import lombok.Getter;

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
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "LitemallUserId{id=" + id + '}';
    }
}