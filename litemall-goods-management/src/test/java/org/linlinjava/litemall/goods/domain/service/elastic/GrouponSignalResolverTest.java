package org.linlinjava.litemall.goods.domain.service.elastic;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.LitemallCombinationMapper;
import org.linlinjava.litemall.db.domain.LitemallCombination;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * groupon_flag matrix: an ACTIVE in-window campaign flags exactly its goods; other goods
 * never flag; out-of-window or non-ACTIVE (draft/expired/offline) campaigns stay dark;
 * a mapper failure degrades to the previous snapshot instead of killing indexing; the
 * read excludes deleted rows (literal deleted = 0 in the hand-written mapper SQL — no
 * Example class, so the inverted andLogicalDeleted() landmine cannot apply).
 */
public class GrouponSignalResolverTest {

    private LitemallCombinationMapper combinationMapper;
    private GrouponSignalResolver resolver;

    @BeforeEach
    public void setUp() {
        combinationMapper = mock(LitemallCombinationMapper.class);
        resolver = new GrouponSignalResolver(combinationMapper);
    }

    private static LitemallCombination campaign(Integer goodsId, int status,
                                                LocalDateTime start, LocalDateTime end) {
        LitemallCombination campaign = new LitemallCombination();
        campaign.setGoodsId(goodsId);
        campaign.setStatus((short) status);
        campaign.setStartTime(start);
        campaign.setEndTime(end);
        return campaign;
    }

    @Test
    public void activeCampaignFlagsItsGoods() {
        when(combinationMapper.selectAll()).thenReturn(List.of(
                campaign(42, 1, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1))));
        assertEquals(1, resolver.grouponFlag(42));
    }

    @Test
    public void openEndedWindowCounts() {
        when(combinationMapper.selectAll()).thenReturn(List.of(campaign(42, 1, null, null)));
        assertEquals(1, resolver.grouponFlag(42));
    }

    @Test
    public void otherGoodsNeverFlag() {
        when(combinationMapper.selectAll()).thenReturn(List.of(
                campaign(42, 1, null, null)));
        assertEquals(0, resolver.grouponFlag(43));
        assertEquals(0, resolver.grouponFlag(null));
    }

    @Test
    public void outOfWindowNeverFlags() {
        when(combinationMapper.selectAll()).thenReturn(List.of(
                campaign(42, 1, LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(1)),
                campaign(43, 1, LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(10))));
        assertEquals(0, resolver.grouponFlag(42)); // ended
        assertEquals(0, resolver.grouponFlag(43)); // not yet started
    }

    @Test
    public void nonActiveStatusNeverFlags() {
        when(combinationMapper.selectAll()).thenReturn(List.of(
                campaign(40, 0, null, null),   // draft
                campaign(41, 2, null, null),   // expired
                campaign(42, 3, null, null))); // offline
        assertEquals(0, resolver.grouponFlag(40));
        assertEquals(0, resolver.grouponFlag(41));
        assertEquals(0, resolver.grouponFlag(42));
    }

    @Test
    public void mapperFailureDegradesToPreviousSnapshotNotAnException() {
        when(combinationMapper.selectAll()).thenThrow(new RuntimeException("db down"));
        assertEquals(0, resolver.grouponFlag(42)); // empty previous snapshot
    }

    @Test
    public void readUsesSelectAllWhoseSqlBindsDeletedZeroLiterally() throws Exception {
        when(combinationMapper.selectAll()).thenReturn(List.of());
        resolver.grouponFlag(42);
        Mockito.verify(combinationMapper).selectAll();
        Method selectAll = LitemallCombinationMapper.class.getMethod("selectAll");
        String sql = String.join(" ", selectAll.getAnnotation(Select.class).value());
        assertTrue(sql.contains("deleted = 0"), "selectAll must exclude logically deleted rows");
    }
}
