package org.linlinjava.litemall.order.domain.model.valueobjects.payment;
import org.linlinjava.litemall.db.dao.*;
import org.linlinjava.litemall.db.domain.*;


import lombok.Getter;

@Getter
public class LitemallBillingAddress {

    private final String firstName;
    private final String lastName;
    private final String line1;
    private final String line2;
    private final String city;
    private final String state;
    private final String postalCode;
    private final String country;
    private final String phone;

    public LitemallBillingAddress(String firstName, String lastName, String line1,
                          String city, String state, String postalCode, String country) {
        this(firstName, lastName, line1, null, city, state, postalCode, country, null);
    }

    public LitemallBillingAddress(String firstName, String lastName, String line1, String line2,
                          String city, String state, String postalCode, String country,
                          String phone) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.line1 = line1;
        this.line2 = line2;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        this.country = country;
        this.phone = phone;
    }
}
