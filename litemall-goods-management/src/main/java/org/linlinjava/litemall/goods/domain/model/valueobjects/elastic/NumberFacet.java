package org.linlinjava.litemall.goods.domain.model.valueobjects.elastic;

public class NumberFacet {
    private String name;
    private Integer value;

    public NumberFacet(){}

    public String getName(){
        return name;
    }

    public void setName(String name){
        this.name = name;
    }
    public Integer getValue(){
        return value;
    }
    public void setValue(Integer value){
        this.value = value;
    }

    public static Builder builder(){
        return new Builder();
    }

    public static class Builder{
        private NumberFacet numberFacet;

        public Builder(){
            this.numberFacet = new NumberFacet();
        }

        public Builder name(String name){
            numberFacet.setName(name);
            return this;
        }

        public Builder value(Integer value){
            numberFacet.setValue(value);
            return this;
        }

        public NumberFacet build(){
            return numberFacet;
        }
    }
}
