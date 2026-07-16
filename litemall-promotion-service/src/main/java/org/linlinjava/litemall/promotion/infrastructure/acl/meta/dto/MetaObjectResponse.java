package org.linlinjava.litemall.promotion.infrastructure.acl.meta.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * Graph API success payload — every publish call returns the created object id;
 * page-photo posts additionally return the containing feed post's id
 * ({@code post_id}), which is the better external reference for a link-out.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetaObjectResponse {

    private String id;

    @JsonProperty("post_id")
    private String postId;

    /** The id to persist as the ledger's external_post_id. */
    public String bestExternalId() {
        return postId != null && !postId.isBlank() ? postId : id;
    }
}
