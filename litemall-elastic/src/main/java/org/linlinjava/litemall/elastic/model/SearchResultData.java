package org.linlinjava.litemall.elastic.model;

import java.math.BigDecimal;
import java.util.Date;


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

    public SearchResultData() {

    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getCounterPrice() {
        return counterPrice;
    }

    public void setCounterPrice(BigDecimal counterPrice) {
        this.counterPrice = counterPrice;
    }

    public BigDecimal getRetailPrice() {
        return retailPrice;
    }

    public void setRetailPrice(BigDecimal retailPrice) {
        this.retailPrice = retailPrice;
    }

    public String getNumberOfProducts() {
        return numberOfProducts;
    }

    public void setNumberOfProducts(String numberOfProducts) {
        this.numberOfProducts = numberOfProducts;
    }

    public String getPicUrl() {
        return picUrl;
    }

    public void setPicUrl(String picUrl) {
        this.picUrl = picUrl;
    }

    public String getGoodsSn() {
        return goodsSn;
    }

    public void setGoodsSn(String goodsSn) {
        this.goodsSn = goodsSn;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Boolean getOnSale() {
        return isOnSale;
    }

    public void setOnSale(Boolean onSale) {
        isOnSale = onSale;
    }

    public Integer getRemainingInStock() {
        return remainingInStock;
    }

    public void setRemainingInStock(Integer remainingInStock) {
        this.remainingInStock = remainingInStock;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Date getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Date lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

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
            searchResultData.setOnSale(onSale);
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