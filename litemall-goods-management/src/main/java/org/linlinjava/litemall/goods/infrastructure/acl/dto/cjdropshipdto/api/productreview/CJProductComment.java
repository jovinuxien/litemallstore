package org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productreview;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class CJProductComment {

    @JsonProperty("commentId")
    private long commentId; // Unique ID of the review

    @JsonProperty("pid")
    private String pid; // Product ID

    @JsonProperty("comment")
    private String comment; // Review comment

    @JsonProperty("commentDate")
    private String commentDate; // Date of the review (in ISO 8601 format)

    @JsonProperty("commentUser")
    private String commentUser; // Name of the reviewer (masked)

    @JsonProperty("score")
    private String score; // Review score (e.g., "5")

    @JsonProperty("commentUrls")
    private List<String> commentUrls; // URLs of images attached to the review

    @JsonProperty("countryCode")
    private String countryCode; // Country code of the reviewer (e.g., "MX")

    @JsonProperty("flagIconUrl")
    private String flagIconUrl; // URL of the country flag icon
}
