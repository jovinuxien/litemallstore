package org.linlinjava.litemall.goods.application.search;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.goods.infrastructure.configuration.CJDropshippingConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

/**
 * Wave 26: which targets tonight's CJ sweep covers.
 *
 * <p>This decides whether the run PRUNES. `null` means "full plan" — stale-prune and native
 * reconcile both run, because both are gated on the targets-override being empty. Anything else is
 * a targeted, additive-only run. Every fallback here therefore returns null on purpose: a
 * misconfiguration must fail toward today's behaviour, never toward silently never pruning again
 * (the 2026-07-13 erosion incident came from judging the whole catalogue against a slice).
 */
public class NightlyTargetSelectionTest {

    private CJDropshippingConfig config;
    private CjCatalogRefreshTask task;

    @BeforeEach
    public void setUp() {
        config = new CJDropshippingConfig();
        task = new CjCatalogRefreshTask(
                mock(CjSnapshotSyncService.class),
                mock(CjDetailEnrichmentService.class),
                mock(CjProductPromotionService.class),
                mock(SearchReindexService.class),
                mock(CategoryImageBackfillService.class),
                config,
                mock(org.linlinjava.litemall.goods.application.inventoryflow.CjSyncRunRecorder.class),
                mock(org.springframework.beans.factory.ObjectProvider.class),
                mock(org.linlinjava.litemall.goods.application.seo.SitemapService.class),
                mock(org.linlinjava.litemall.goods.application.seo.MetaCatalogFeedService.class));
    }

    private CJDropshippingConfig.CatalogTarget target(String category, boolean nightly) {
        CJDropshippingConfig.CatalogTarget t = new CJDropshippingConfig.CatalogTarget();
        t.setCategory(category);
        t.setNightly(nightly);
        return t;
    }

    /** Yesterday's behaviour must survive an unconfigured deployment untouched. */
    @Test
    public void everyTargetNightlyMeansAFullRun() {
        config.setFullSyncDay("SUNDAY");
        config.setCatalogTargets(List.of(target("A", true), target("B", true)));

        assertNull(task.nightlyTargets(),
                "passing the complete list as an override would disable pruning forever");
    }

    @Test
    public void aSubsetIsReturnedOnOrdinaryDays() {
        config.setFullSyncDay(otherDayThanToday().name());
        config.setCatalogTargets(List.of(target("anchor", true), target("rest", false)));

        List<CJDropshippingConfig.CatalogTarget> subset = task.nightlyTargets();

        assertNotNull(subset, "an ordinary day with excluded targets must run the subset");
        assertEquals(1, subset.size());
        assertEquals("anchor", subset.get(0).getCategory());
    }

    @Test
    public void theFullSyncDayRunsEverything() {
        config.setFullSyncDay(LocalDate.now().getDayOfWeek().name());
        config.setCatalogTargets(List.of(target("anchor", true), target("rest", false)));

        assertNull(task.nightlyTargets(), "the weekly full run is what prunes and reconciles");
    }

    @Test
    public void anUnsetOrTypoedDayFallsBackToTheFullPlan() {
        config.setCatalogTargets(List.of(target("anchor", true), target("rest", false)));

        config.setFullSyncDay(null);
        assertNull(task.nightlyTargets(), "unset ⇒ pre-Wave-26 behaviour");
        config.setFullSyncDay("   ");
        assertNull(task.nightlyTargets(), "blank ⇒ pre-Wave-26 behaviour");
        config.setFullSyncDay("Sundy");
        assertNull(task.nightlyTargets(), "a typo must not quietly stop pruning");
    }

    /** Excluding everything would fetch nothing at all — fall back rather than sweep an empty plan. */
    @Test
    public void excludingEveryTargetFallsBackToTheFullPlan() {
        config.setFullSyncDay(otherDayThanToday().name());
        config.setCatalogTargets(List.of(target("A", false), target("B", false)));

        assertNull(task.nightlyTargets());
    }

    @Test
    public void noTargetsConfiguredIsAFullRun() {
        config.setFullSyncDay(otherDayThanToday().name());
        config.setCatalogTargets(List.of());

        assertNull(task.nightlyTargets());
    }

    private DayOfWeek otherDayThanToday() {
        return LocalDate.now().getDayOfWeek().plus(1);
    }
}
