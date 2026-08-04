package org.linlinjava.litemall.promotion.infrastructure.acl.postiz.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * One row of Postiz {@code GET /public/v1/integrations}. Ignore-unknown is
 * explicit because the shared core JacksonConfig does NOT ignore unknown
 * fields, and Postiz also sends {@code profile}/{@code customer} we don't use.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class PostizIntegrationDto {

    /** Postiz integration (channel) id — a cuid string. */
    private String id;

    /** Display name (page/account name). */
    private String name;

    /** Provider identifier: {@code facebook | x | instagram | ...}. */
    private String identifier;

    /** Avatar URL. */
    private String picture;

    /** True when Postiz has the channel disabled (e.g. expired token). */
    private Boolean disabled;
}
