package org.linlinjava.litemall.elastic.model;

import java.util.List;
import java.util.Map;

public class ProductDocument {
    private CategoryDoc categoryDoc;
    private Map<String, Integer> categoryScores;
    private List<String> completionTerms;
    private NumberSort numberSort;
    private Scores scores;
    private List<SearchData> searchData;
    private SearchResultData searchResultData;
    private StringSort stringSort;


    public ProductDocument() {}

    public CategoryDoc getCategoryDoc() {
        return categoryDoc;
    }

    public void setCategoryDoc(CategoryDoc categoryDoc) {
        this.categoryDoc = categoryDoc;
    }

    public Map<String, Integer> getCategoryScores() {
        return categoryScores;
    }


    public void setCategoryScores(Map<String, Integer> categoryScores) {
        this.categoryScores = categoryScores;
    }

    public List<String> getCompletionTerms() {
        return completionTerms;
    }

    public void setCompletionTerms(List<String> completionTerms) {
        this.completionTerms = completionTerms;
    }

    public NumberSort getNumberSort() {
        return numberSort;
    }

    public void setNumberSort(NumberSort numberSort) {
        this.numberSort = numberSort;
    }

    public Scores getScores() {
        return scores;
    }

    public void setScores(Scores scores) {
        this.scores = scores;
    }

    public List<SearchData> getSearchData() {
        return searchData;
    }

    public void setSearchData(List<SearchData> searchData) {
        this.searchData = searchData;
    }

    public SearchResultData getSearchResultData() {
        return searchResultData;
    }

    public void setSearchResultData(SearchResultData searchResultData) {
        this.searchResultData = searchResultData;
    }

    public StringSort getStringSort() {
        return stringSort;
    }

    public void setStringSort(StringSort stringSort) {
        this.stringSort = stringSort;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ProductDocument productDocument;

        public Builder() {
            this.productDocument = new ProductDocument();
        }

        public Builder categoryDoc(CategoryDoc categoryDoc) {
            productDocument.setCategoryDoc(categoryDoc);
            return this;
        }

        public Builder categoryScores(Map<String, Integer> categoryScores) {
            productDocument.setCategoryScores(categoryScores);
            return this;
        }

        public Builder completionTerms(List<String> completionTerms) {
            productDocument.setCompletionTerms(completionTerms);
            return this;
        }

        public Builder numberSort(NumberSort numberSort) {
            productDocument.setNumberSort(numberSort);
            return this;
        }

        public Builder scores(Scores scores) {
            productDocument.setScores(scores);
            return this;
        }

        public Builder searchData(List<SearchData> searchData) {
            productDocument.setSearchData(searchData);
            return this;
        }

        public Builder searchResultData(SearchResultData searchResultData) {
            productDocument.setSearchResultData(searchResultData);
            return this;
        }

        public Builder stringSort(StringSort stringSort) {
            productDocument.setStringSort(stringSort);
            return this;
        }

        public ProductDocument build() {
            return productDocument;
        }
    }
}
