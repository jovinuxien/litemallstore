package org.linlinjava.litemall.promotion.interfaces.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Body of Wave-17 {@code POST /srv/private/admin/promotion/postiz/preview} and
 * {@code /publish} (identical per the contract): admin-picked products +
 * Postiz channels + start/interval scheduling. {@code categoryId} is optional
 * and informational (ledger only).
 */
@Getter
@Setter
public class PostizBatchRequest {

    private List<Integer> goodsIds;

    /** Postiz integration ids from {@code GET /channels}. */
    private List<String> channelIds;

    /** ISO-8601; offset-less values are read as UTC. */
    private String startTime;

    /** Gap between consecutive product posts; 0 = all at the start instant. */
    private Integer intervalMinutes;

    private Integer categoryId;
}
