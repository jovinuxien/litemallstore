package org.linlinjava.litemall.elastic.model;

public class StringFacet {
    private String name;
    private String values;

    public StringFacet(){}

    public String getName(){
        return name;
    }

    public void setName(String name){
        this.name = name;
    }

    public String getValues(){
        return values;
    }

    public void setValues(String values){
        this.values = values;
    }

    public static Builder builder(){
        return new Builder();
    }

    public static class Builder{
        private StringFacet stringFacet;

        public Builder(){
            this.stringFacet = new StringFacet();
        }

        public Builder name(String name){
            stringFacet.setName(name);
            return this;
        }

        public Builder values(String values){
            stringFacet.setValues(values);
            return this;
        }

        public StringFacet build(){
            return stringFacet;
        }

    }
}
