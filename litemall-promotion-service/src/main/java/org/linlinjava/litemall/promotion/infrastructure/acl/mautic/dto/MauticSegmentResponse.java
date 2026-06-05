package org.linlinjava.litemall.promotion.infrastructure.acl.mautic.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mautic response for a segment create/get — the segment is wrapped under
 * {@code "list"}. Only the id is consumed.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MauticSegmentResponse {

    private Segment list;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Segment {
        private Integer id;
        private String alias;
        private String name;
    }
}
