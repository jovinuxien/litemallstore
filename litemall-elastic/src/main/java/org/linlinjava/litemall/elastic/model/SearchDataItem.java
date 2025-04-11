package org.linlinjava.litemall.elastic.model;

import java.util.List;

public class SearchDataItem {
    private String fullTextSearch;
    private String fulltextBoosted;
    private List<StringFacet> stringFacets;
    private List<NumberFacet> numberFacets;

    public String getFullTextSearch() {
        return fullTextSearch;
    }

    public void setFullTextSearch(String fullTextSearch) {
        this.fullTextSearch = fullTextSearch;
    }

    public String getFulltextBoosted() {
        return fulltextBoosted;
    }

    public void setFulltextBoosted(String fulltextBoosted) {
        this.fulltextBoosted = fulltextBoosted;
    }

    public List<StringFacet> getStringFacets() {
        return stringFacets;
    }

    public void setStringFacets(List<StringFacet> stringFacets) {
        this.stringFacets = stringFacets;
    }

    public List<NumberFacet> getNumberFacets() {
        return numberFacets;
    }

    public void setNumberFacets(List<NumberFacet> numberFacets) {
        this.numberFacets = numberFacets;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private SearchDataItem searchData;

        public Builder() {
            this.searchData = new SearchDataItem();
        }

        public Builder fullTextSearch(String fullTextSearch) {
            searchData.setFullTextSearch(fullTextSearch);
            return this;
        }

        public Builder fulltextBoosted(String fulltextBoosted) {
            searchData.setFulltextBoosted(fulltextBoosted);
            return this;
        }

        public Builder stringFacets(List<StringFacet> stringFacets) {
            searchData.setStringFacets(stringFacets);
            return this;
        }

        public Builder numberFacets(List<NumberFacet> numberFacets) {
            searchData.setNumberFacets(numberFacets);
            return this;
        }

        public SearchDataItem build() {
            return searchData;
        }
    }
}
