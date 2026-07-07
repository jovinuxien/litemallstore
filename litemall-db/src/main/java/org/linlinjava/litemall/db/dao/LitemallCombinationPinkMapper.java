package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.LitemallCombinationPink;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Hand-written mapper for combination group-buy participation
 * ({@code litemall_combination_pink}, V30). Co-located with the generated DAOs
 * so it is picked up by {@code @MapperScan} and the {@code dao/*.xml}
 * mapper-location pattern.
 */
public interface LitemallCombinationPinkMapper {

    /** Insert one participant slot. Populates the generated id back onto the record. */
    int insert(LitemallCombinationPink record);

    LitemallCombinationPink selectByPrimaryKey(@Param("id") Integer id);

    /** Update mutable state (status, orderId, updateTime) of one slot. */
    int update(LitemallCombinationPink record);

    /** All slots of a group: the leader row itself plus its members. */
    List<LitemallCombinationPink> selectGroup(@Param("headId") Integer headId);

    /** Member count of a group including the leader. */
    int countGroup(@Param("headId") Integer headId);

    /** Every slot a user holds, newest first (my groups). */
    List<LitemallCombinationPink> selectByUser(@Param("userId") Integer userId);

    /** Pending LEADER rows whose group expired before {@code now} (sweep input). */
    List<LitemallCombinationPink> selectExpiredPendingLeaders(@Param("now") LocalDateTime now);

    /** Leader rows of a campaign, optionally by status, newest first (admin monitoring). */
    List<LitemallCombinationPink> selectLeaders(@Param("combinationId") Integer combinationId,
                                                @Param("status") Integer status);
}
