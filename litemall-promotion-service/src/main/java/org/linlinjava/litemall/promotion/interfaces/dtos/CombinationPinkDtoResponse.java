package org.linlinjava.litemall.promotion.interfaces.dtos;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * View of one group-buy participant slot; on group views the leader slot also
 * carries the current member count and (optionally) the member slots.
 */
@Getter
@Setter
@Builder
public class CombinationPinkDtoResponse {

    private Integer pinkId;
    private Integer combinationId;
    /** Leader slot id; null when this slot is the leader. */
    private Integer headId;
    private Integer userId;
    private Integer orderId;
    private Integer requiredMembers;
    private Integer memberCount;
    private LocalDateTime expireTime;
    private String status;
    private List<CombinationPinkDtoResponse> members;
}
