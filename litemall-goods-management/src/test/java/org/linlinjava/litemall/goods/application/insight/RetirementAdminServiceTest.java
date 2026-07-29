package org.linlinjava.litemall.goods.application.insight;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.InsightMapper;
import org.linlinjava.litemall.db.dao.LitemallRetireCandidateMapper;
import org.linlinjava.litemall.db.domain.LitemallRetireCandidate;
import org.linlinjava.litemall.goods.infrastructure.configuration.InventoryFlowProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wave-14 retirement admin semantics: real 653 on missing/lost-race decisions (no silent
 * warn-and-succeed), batch approve is partial-apply with an honest failure report, executeOn
 * defaults to the NEXT configured weekday (strictly after today), list rows dedupe per goods
 * and carry ISO-string dates.
 */
public class RetirementAdminServiceTest {

    private InsightMapper insightMapper;
    private LitemallRetireCandidateMapper retireMapper;
    private InventoryFlowProperties properties;
    private RetirementAdminService service;

    @BeforeEach
    public void setUp() {
        insightMapper = mock(InsightMapper.class);
        retireMapper = mock(LitemallRetireCandidateMapper.class);
        properties = new InventoryFlowProperties();
        service = new RetirementAdminService(insightMapper, retireMapper, properties, new ObjectMapper());
    }

    private static LitemallRetireCandidate proposed(int id, int goodsId) {
        LitemallRetireCandidate c = new LitemallRetireCandidate();
        c.setId(id);
        c.setGoodsId(goodsId);
        c.setDay(LocalDate.now());
        c.setStatus(LitemallRetireCandidate.STATUS_PROPOSED);
        return c;
    }

    @Test
    public void approveAllSucceedsWithExplicitDate() {
        when(retireMapper.selectLatestProposedByGoods(41)).thenReturn(proposed(1, 41));
        when(retireMapper.selectLatestProposedByGoods(42)).thenReturn(proposed(2, 42));
        when(retireMapper.updateStatus(anyInt(), any(), any(), any())).thenReturn(1);

        LocalDate executeOn = LocalDate.of(2026, 8, 5);
        GovernanceResult result = service.approve(List.of(41, 42), executeOn);

        assertNull(result.error());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.data();
        assertEquals(List.of(41, 42), data.get("approved"));
        assertEquals("2026-08-05", data.get("executeOn"));
        verify(retireMapper).updateStatus(1, LitemallRetireCandidate.STATUS_PROPOSED,
                LitemallRetireCandidate.STATUS_APPROVED, executeOn);
    }

    @Test
    public void approvePartialFailureIs653ButAppliesTheRest() {
        when(retireMapper.selectLatestProposedByGoods(41)).thenReturn(proposed(1, 41));
        when(retireMapper.selectLatestProposedByGoods(42)).thenReturn(null); // no proposal
        when(retireMapper.updateStatus(anyInt(), any(), any(), any())).thenReturn(1);

        GovernanceResult result = service.approve(List.of(41, 42), LocalDate.of(2026, 8, 5));

        assertEquals(Integer.valueOf(653), result.errno());
        assertTrue(result.error().contains("42"), result.error());
        // the approvable one was still approved before reporting
        verify(retireMapper).updateStatus(eq(1), anyString(), anyString(), any());
    }

    @Test
    public void approveLostCasRaceIs653() {
        when(retireMapper.selectLatestProposedByGoods(41)).thenReturn(proposed(1, 41));
        when(retireMapper.updateStatus(anyInt(), any(), any(), any())).thenReturn(0);

        GovernanceResult result = service.approve(List.of(41), null);

        assertEquals(Integer.valueOf(653), result.errno());
    }

    @Test
    public void dismissWithoutProposalIs653() {
        when(retireMapper.selectLatestProposedByGoods(42)).thenReturn(null);
        assertEquals(Integer.valueOf(653), service.dismiss(42).errno());
    }

    @Test
    public void dismissLostRaceIs653() {
        when(retireMapper.selectLatestProposedByGoods(42)).thenReturn(proposed(2, 42));
        when(retireMapper.updateStatus(anyInt(), any(), any(), any())).thenReturn(0);
        assertEquals(Integer.valueOf(653), service.dismiss(42).errno());
    }

    @Test
    public void nextScheduledDayIsStrictlyAfterToday() {
        properties.setRetireDefaultDay("WEDNESDAY");
        LocalDate aWednesday = LocalDate.of(2026, 7, 29); // a Wednesday
        assertEquals(DayOfWeek.WEDNESDAY, aWednesday.getDayOfWeek());
        assertEquals(aWednesday.plusDays(7), service.nextScheduledDay(aWednesday));
        assertEquals(aWednesday, service.nextScheduledDay(aWednesday.minusDays(1)));
    }

    @Test
    public void shortDayNamesAreAccepted() {
        properties.setRetireDefaultDay("WED");
        LocalDate tuesday = LocalDate.of(2026, 7, 28);
        assertEquals(DayOfWeek.WEDNESDAY, service.nextScheduledDay(tuesday).getDayOfWeek());
    }

    @Test
    public void listDedupesPerGoodsAndSerializesIsoDates() {
        Map<String, Object> newer = new HashMap<>();
        newer.put("goodsId", 42);
        newer.put("reasons", "[\"unavailable at CJ for 4 days\"]");
        newer.put("day", Date.valueOf("2026-07-29"));
        newer.put("executeOn", null);
        Map<String, Object> older = new HashMap<>(newer);
        older.put("day", Date.valueOf("2026-07-25"));
        when(insightMapper.selectRetireCandidateRows(eq("proposed"), anyInt()))
                .thenReturn(List.of(newer, older));

        GovernanceResult result = service.list(null); // defaults to proposed

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.data();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("list");
        assertEquals(1, list.size());
        assertEquals("2026-07-29", list.get(0).get("day"));
        assertEquals(List.of("unavailable at CJ for 4 days"), list.get(0).get("reasons"));
    }

    @Test
    public void unknownStatusIsRejected() {
        assertEquals(Integer.valueOf(402), service.list("bogus").errno());
    }
}
