package org.linlinjava.litemall.core.infrastructure.acl.dto.cjdropshipdto.api.productreview;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class CJProductReviewData {

    @JsonProperty("pageNum")
    private String pageNum; // Current page number

    @JsonProperty("pageSize")
    private String pageSize; // Number of results per page

    @JsonProperty("total")
    private String total; // Total number of reviews

    @JsonProperty("list")
    private List<CJProductComment> list; // List of reviews
}
