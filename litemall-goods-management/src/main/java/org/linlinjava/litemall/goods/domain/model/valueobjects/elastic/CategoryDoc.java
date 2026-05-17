package org.linlinjava.litemall.goods.domain.model.valueobjects.elastic;

import java.util.List;

public class CategoryDoc {
    private List<String> directParents;
    private List<String> allParents;
    private List<String> paths;

    public List<String> getDirectParents(){
        return directParents;
    }

    public void setDirectParents(List<String> directParents){
        this.directParents = directParents;
    }

    public List<String> getAllParents(){
        return allParents;
    }

    public void setAllParents(List<String> allParents){
        this.allParents = allParents;
    }

    public List<String> getPaths(){
        return paths;
    }

    public void setPaths(List<String> paths){
        this.paths = paths;
    }

    public static Builder builder(){
        return new Builder();
    }

    public static class Builder{
        private CategoryDoc categoryElasticDoc;

        public Builder(){
            this.categoryElasticDoc = new CategoryDoc();
        }

        public Builder directParents(List<String> directParents){
            categoryElasticDoc.setDirectParents(directParents);
            return this;
        }

        public Builder allParents(List<String> allParents){
            categoryElasticDoc.setAllParents(allParents);
            return this;
        }

        public Builder paths(List<String> paths){
            categoryElasticDoc.setPaths(paths);
            return this;
        }

        public CategoryDoc build(){
            return categoryElasticDoc;
        }
    }
}
