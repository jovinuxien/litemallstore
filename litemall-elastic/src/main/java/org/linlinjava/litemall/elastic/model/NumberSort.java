package org.linlinjava.litemall.elastic.model;

import java.math.BigDecimal;

public class NumberSort {

    private BigDecimal retailPrice;


    public NumberSort(){}

    public BigDecimal getRetailPrice(){
        return retailPrice;
    }

    public void setRetailPrice(BigDecimal retailPrice){
        this.retailPrice = retailPrice;
    }

    public static Builder builder (){
        return new Builder();
    }

    public static class Builder{
        private NumberSort numberSort;

        public Builder(){
            this.numberSort = new NumberSort();
        }

        public Builder retailPrice(BigDecimal retailPrice){
            numberSort.setRetailPrice(retailPrice);
            return this;
        }

        public NumberSort build(){
            return numberSort;
        }
    }
}

