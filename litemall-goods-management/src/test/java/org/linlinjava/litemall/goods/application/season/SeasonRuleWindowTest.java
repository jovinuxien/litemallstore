package org.linlinjava.litemall.goods.application.season;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.domain.LitemallSeasonRule;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Season windows recur yearly and one of them wraps the year end, which is the only reason this
 * is not a plain string range check. Winter running 12-01 → 02-28 must cover January.
 */
public class SeasonRuleWindowTest {

    private static LitemallSeasonRule rule(String start, String end) {
        LitemallSeasonRule rule = new LitemallSeasonRule();
        rule.setWindowStartMd(start);
        rule.setWindowEndMd(end);
        return rule;
    }

    @Test
    public void anOrdinaryWindowCoversItsOwnMonths() {
        LitemallSeasonRule autumn = rule("09-01", "11-30");
        assertTrue(autumn.coversMonthDay("09-01"), "inclusive at the start");
        assertTrue(autumn.coversMonthDay("10-15"));
        assertTrue(autumn.coversMonthDay("11-30"), "inclusive at the end");
        assertFalse(autumn.coversMonthDay("08-31"));
        assertFalse(autumn.coversMonthDay("12-01"));
    }

    @Test
    public void aWindowThatWrapsTheYearEndCoversJanuary() {
        LitemallSeasonRule winter = rule("12-01", "02-28");
        assertTrue(winter.coversMonthDay("12-25"));
        assertTrue(winter.coversMonthDay("01-15"), "January is winter, not a gap");
        assertTrue(winter.coversMonthDay("02-28"));
        assertFalse(winter.coversMonthDay("06-01"));
        assertFalse(winter.coversMonthDay("03-01"));
    }

    /** An unusable window must never claim a date rather than claiming every date. */
    @Test
    public void aMissingBoundCoversNothing() {
        assertFalse(rule(null, "11-30").coversMonthDay("10-01"));
        assertFalse(rule("09-01", null).coversMonthDay("10-01"));
        assertFalse(rule("09-01", "11-30").coversMonthDay(null));
    }
}
