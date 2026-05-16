package org.linlinjava.litemall.goods.domain.model.valueobjects.elastic;

public class StringSort {

    private  String name;

    private String getName(){
        return name;
    }

    private void setName(String name){
        this.name = name;
    }

    public StringSort(){

    }

    public static Builder builder(){
        return new Builder();
    }

    public static class Builder{
        private StringSort stringSort;

        public Builder(){
            this.stringSort = new StringSort();
        }

        public Builder name(String name){
            stringSort.setName(name);
            return this;
        }

        public StringSort build(){
            return stringSort;
        }
    }
}
