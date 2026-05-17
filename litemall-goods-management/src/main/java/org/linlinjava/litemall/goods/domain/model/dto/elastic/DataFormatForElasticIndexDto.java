package org.linlinjava.litemall.goods.domain.model.dto.elastic;


import org.linlinjava.litemall.goods.domain.model.valueobjects.elastic.*;

import java.util.List;

public class DataFormatForElasticIndexDto {
    private String type;
    private SearchResultData searchDataResult;
    private SearchDataItem searchData;
    private List<String> completionTerms;
    private List<String> suggestionsTerms;
    private Integer numberSort;
    private String stringSort;
    private Scores scores;
    private CategoryDoc category;
    private CategoryScores categoryScores;
}
