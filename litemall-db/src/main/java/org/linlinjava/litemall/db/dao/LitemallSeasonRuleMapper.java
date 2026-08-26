package org.linlinjava.litemall.db.dao;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.LitemallSeasonRule;

/**
 * Hand-written mapper for season definitions ({@code litemall_season_rule}, V64;
 * UNIQUE season_key). No {@code Example} machinery.
 *
 * <p>Rules are editable at runtime through the admin surface — if weights could only change by
 * redeploy, calling them "configuration" would be a lie.
 */
public interface LitemallSeasonRuleMapper {

    /** All non-deleted rules, enabled first then by key, for the admin list and the scorer. */
    List<LitemallSeasonRule> selectAll(@Param("enabledOnly") boolean enabledOnly);

    /** One rule by its key, or null. */
    LitemallSeasonRule selectByKey(@Param("seasonKey") String seasonKey);

    /** Patch the tunable fields; season_key is identity and never moves. */
    int updateByKey(LitemallSeasonRule rule);
}
