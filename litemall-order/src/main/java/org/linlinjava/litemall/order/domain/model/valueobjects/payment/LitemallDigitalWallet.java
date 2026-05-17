package org.linlinjava.litemall.order.domain.model.valueobjects.payment;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;


import lombok.Getter;

@Getter
public class LitemallDigitalWallet {

    private final String walletType;
    private final String walletId;
    private final String email;
    private final String phoneNumber;

    public LitemallDigitalWallet(String walletType, String walletId) {
        this(walletType, walletId, null, null);
    }

    public LitemallDigitalWallet(String walletType, String walletId, String email, String phoneNumber) {
        this.walletType = walletType;
        this.walletId = walletId;
        this.email = email;
        this.phoneNumber = phoneNumber;
    }

}
