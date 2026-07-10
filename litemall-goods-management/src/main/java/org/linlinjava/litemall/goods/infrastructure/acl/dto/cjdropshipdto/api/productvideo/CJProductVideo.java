package org.linlinjava.litemall.goods.infrastructure.acl.dto.cjdropshipdto.api.productvideo;

import lombok.Data;

/**
 * One product video from CJ {@code product/queryVideosByProductId}. NOTE: downloading the
 * {@code videoUrl} requires a {@code Referer: https://developers.cjdropshipping.com/} header;
 * we only relay the URL.
 */
@Data
public class CJProductVideo {
    private String id;
    private String videoName;
    private String videoState; // e.g. "ON_STATE"
    private String videoUrl;
    private String copyright;
    private String isFree;
    private String videoSize;  // bytes, as a string
    private Double duration;   // seconds
    private Integer width;
    private Integer height;
}
