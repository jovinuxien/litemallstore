package org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.cjcategory;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class CJCategoryDataResponse {

    private int code ;
    private boolean result;
    private String message;
    private List<CategoryData> data;
    private String requestId;

    @Data
    public static class CategoryData {
        @JsonProperty("categoryFirstName")
        private String categoryFirstName;

        @JsonProperty("categoryFirstList")
        private List<CategorySecond> categoryFirstList;
    }

    @Data
    public static class CategorySecond {
        @JsonProperty("categorySecondName")
        private String categorySecondName;

        @JsonProperty("categorySecondList")
        private List<CategoryThird> categorySecondList;
    }

    @Data
    public static class CategoryThird {
        @JsonProperty("categoryId")
        private String categoryId;

        @JsonProperty("categoryName")
        private String categoryName;
    }

}
