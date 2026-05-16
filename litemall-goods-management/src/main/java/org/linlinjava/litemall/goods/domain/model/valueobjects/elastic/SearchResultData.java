package org.linlinjava.litemall.goods.domain.model.valueobjects.elastic;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;


@Getter
@Setter
public class SearchResultData {

    private String name;
    private String brand;
    private String category;
    private String picUrl;
    private BigDecimal counterPrice;
    private BigDecimal retailPrice;
    private String numberOfProducts;
    private String goodsSn;
    private String url;
    private Boolean isOnSale;
    private Integer remainingInStock;

    private Date lastUpdated;

    public SearchResultData() {}

    public static Builder builder(){
        return new Builder();
    }

    public static class Builder{
        private SearchResultData searchResultData;


        public Builder(){
            this.searchResultData = new SearchResultData();
        }

        public Builder name(String name){
            searchResultData.setName(name);
            return this;
        }

        public Builder brand(String brand){
            searchResultData.setBrand(brand);
            return this;
        }
        public Builder category(String category){
            searchResultData.setCategory(category);
            return this;
        }
        public Builder picUrl(String picUrl){
            searchResultData.setPicUrl(picUrl);
            return this;
        }
        public Builder counterPrice(BigDecimal counterPrice){
            searchResultData.setCounterPrice(counterPrice);
            return this;
        }
        public Builder retailPrice(BigDecimal retailPrice){
            searchResultData.setRetailPrice(retailPrice);
            return this;
        }
        public Builder numberOfProducts(String numberOfProducts){
            searchResultData.setNumberOfProducts(numberOfProducts);
            return this;
        }
        public Builder goodsSn(String goodsSn){
            searchResultData.setGoodsSn(goodsSn);
            return this;
        }
        public Builder url(String url){
            searchResultData.setUrl(url);
            return this;
        }
        public Builder onSale(Boolean onSale){
            searchResultData.setIsOnSale(onSale);
            return this;
        }
        public Builder remainingInStock(Integer remainingInStock){
            searchResultData.setRemainingInStock(remainingInStock);
            return this;
        }
        public Builder lastUpdated(Date lastUpdated){
            searchResultData.setLastUpdated(lastUpdated);
            return this;
        }
        public SearchResultData build(){
            return searchResultData;
        }
    }


}