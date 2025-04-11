package org.linlinjava.litemall.elastic.model;

public class CategoryScores {
    private Integer numberOfImpressions;
    private Integer numberOfOrders;

    public CategoryScores(){}

    public Integer getNumberOfImpressions(){
        return numberOfImpressions;
    }

    public void setNumberOfImpressions(Integer numberOfImpressions){
        this.numberOfImpressions = numberOfImpressions;
    }

    public Integer getNumberOfOrders(){
        return numberOfOrders;
    }

    public void setNumberOfOrders(Integer numberOfOrders){
        this.numberOfOrders = numberOfOrders;
    }

    public static Builder builder(){
        return new Builder();
    }

    public static class Builder{
        private CategoryScores categoryScores;

        public Builder(){
            this.categoryScores = new CategoryScores();
        }

        public Builder numberOfImpressions(Integer numberOfImpressions){
            categoryScores.setNumberOfImpressions(numberOfImpressions);
            return this;
        }

        public Builder numberOfOrders(Integer numberOfOrders){
            categoryScores.setNumberOfOrders(numberOfOrders);
            return this;
        }

        public CategoryScores build(){
            return categoryScores;
        }
    }
}
