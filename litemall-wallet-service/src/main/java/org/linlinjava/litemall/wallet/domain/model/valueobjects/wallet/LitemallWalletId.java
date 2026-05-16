package org.linlinjava.litemall.wallet.domain.model.valueobjects.wallet;

import lombok.Getter;

@Getter
public class LitemallWalletId {

    private final Integer id;

    public LitemallWalletId(Integer id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Wallet ID must be positive");
        }
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LitemallWalletId that = (LitemallWalletId) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "LitemallWalletId{id=" + id + '}';
    }
}