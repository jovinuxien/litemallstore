package org.linlinjava.litemall.wx.dto;


import java.math.BigDecimal;

public class ElasticData {
    private String goodsName;
    private String brief;
    private BigDecimal counterPrice;
    private BigDecimal retailPrice;
    private String categoryName;
    private Integer directParent;
    private Integer categoryId;
    private Integer categoryLevel;
    private String manufacturer;
    private String attributeName;


    public String getGoodsName() {
        return goodsName;
    }

    public void setGoodsName(String goodsName) {
        this.goodsName = goodsName;
    }

    public String getBrief() {
        return brief;
    }

    public void setBrief(String brief) {
        this.brief = brief;
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

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Integer getDirectParent() {
        return directParent;
    }

    public void setDirectParent(Integer directParent) {
        this.directParent = directParent;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public Integer getCategoryLevel() {
        return categoryLevel;
    }

    public void setCategoryLevel(Integer categoryLevel) {
        this.categoryLevel = categoryLevel;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getAttributeName() {
        return attributeName;
    }

    public void setAttributeName(String attributeName) {
        this.attributeName = attributeName;
    }
}
