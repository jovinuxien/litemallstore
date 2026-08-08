package org.linlinjava.litemall.db.dao;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.LitemallSearchStatDaily;

/**
 * Hand-written mapper for the Wave-22 search demand rollup
 * ({@code litemall_search_stat_daily}, V57; UNIQUE day + keyword — the
 * {@link LitemallPromoCandidateMapper} pattern, no {@code Example} machinery).
 *
 * <p>The rollup is a full per-day recompute: {@link #upsertDay} overwrites the
 * (day, keyword) row's counts absolutely, so re-running a day is idempotent.
 * The two source-aggregation selects read {@code litemall_search_history} and
 * the Phase-0 behavioral log {@code litemall_user_event} (read-only,
 * aggregate-only — no visitor/user ids ever leave the SQL).
 */
public interface LitemallSearchStatMapper {

    /** Insert or absolutely overwrite the (day, keyword) row's counts. */
    int upsertDay(LitemallSearchStatDaily stat);

    /**
     * Per-keyword aggregation over a day range (inclusive), searches desc:
     * rows {@code {keyword, searches, zeroResults, clicks}}.
     */
    List<Map<String, Object>> selectRange(@Param("from") LocalDate from,
                                          @Param("to") LocalDate to,
                                          @Param("limit") int limit);

    /** Per-keyword aggregation over a day range, zero-result queries only, zeroResults desc. */
    List<Map<String, Object>> selectRangeZeroTop(@Param("from") LocalDate from,
                                                 @Param("to") LocalDate to,
                                                 @Param("limit") int limit);

    /** Range totals: {@code {searches, zeroResults, clicks}} (0s when empty). */
    Map<String, Object> selectRangeTotals(@Param("from") LocalDate from,
                                          @Param("to") LocalDate to);

    /**
     * Rollup source (a): logged-in history rows in {@code [start, end)} grouped by
     * normalized keyword — {@code {keyword, searches, zeroResults}}. zeroResults counts
     * rows with {@code result_count = 0}; NULL result_count is unknown, never zero.
     * Counts logically-deleted rows too: clearing the history rail removes the user's
     * list, not the fact a search happened (the aggregate carries no identity).
     */
    List<Map<String, Object>> selectHistoryAgg(@Param("start") LocalDateTime start,
                                               @Param("end") LocalDateTime end);

    /**
     * Rollup source (b1): anonymous consented {@code search} events in {@code [start, end)}
     * grouped by normalized query — {@code {keyword, searches}}. Only {@code user_id IS NULL}
     * rows count: logged-in searches are already counted once via the history source.
     */
    List<Map<String, Object>> selectSearchEventAgg(@Param("start") LocalDateTime start,
                                                   @Param("end") LocalDateTime end);

    /**
     * Rollup source (b2): {@code click_result} events carrying a query in {@code [start, end)}
     * grouped by normalized query — {@code {keyword, clicks}} (clicks have this single source).
     */
    List<Map<String, Object>> selectClickEventAgg(@Param("start") LocalDateTime start,
                                                  @Param("end") LocalDateTime end);
}
