package org.linlinjava.litemall.goods.infrastructure.configuration;

import org.linlinjava.litemall.goods.application.inventoryflow.AvailabilityRecorder;
import org.linlinjava.litemall.goods.application.inventoryflow.CatalogLandedSummary;
import org.linlinjava.litemall.goods.application.inventoryflow.CategoryInsightCache;
import org.linlinjava.litemall.goods.application.inventoryflow.CjSyncRunRecorder;
import org.linlinjava.litemall.goods.application.inventoryflow.DealCandidateScorer;
import org.linlinjava.litemall.goods.application.inventoryflow.InventoryContextEnricher;
import org.linlinjava.litemall.goods.application.inventoryflow.InventoryRecheckActivator;
import org.linlinjava.litemall.goods.application.inventoryflow.MarginRecorder;
import org.linlinjava.litemall.goods.application.inventoryflow.ProductFlowEvent;
import org.linlinjava.litemall.goods.application.inventoryflow.ProductInventoryContext;
import org.linlinjava.litemall.goods.application.inventoryflow.RetireCandidateScorer;
import org.linlinjava.litemall.goods.application.inventoryflow.RetirementGovernor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.integration.channel.ExecutorChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.ArrayList;
import java.util.List;

/**
 * Wave-12 CJ inventory-intelligence flow (Fisher et al., Java DSL — the book's XML maps 1:1):
 *
 * <pre>
 * gateway (ch5)          InventoryFlowGateway.onCatalogLanded / onEnrichmentBatch
 * wire tap (ch14)        CjSyncRunRecorder — litemall_cj_sync_run 'flow' rows
 * splitter (ch7)         summary → per-pid ProductFlowEvent (sequence headers drive the aggregator)
 * content enricher (ch5) InventoryContextEnricher — join goods/SKU/signal state
 * router (ch6)           NEW_ARRIVAL / UPDATED / VANISHED channels
 * activators (ch5)       MarginRecorder, DealCandidateScorer, AvailabilityRecorder
 * aggregator (ch7)       group completion → CategoryInsightCache refresh + run-row close
 * poller (ch15)          bounded QueueChannel + 1 msg/s poll → getInventory(vid) recheck
 * </pre>
 *
 * <p>Threading: an explicit {@code taskScheduler} bean is declared because {@code @EnableIntegration}
 * and {@code @EnableScheduling} otherwise contend for the default bean name — and
 * {@code allow-bean-definition-overriding: true} would mask the collision instead of failing.
 * Per-product work rides its own small executor, NOT litemall-core's {@code asyncIndexingExecutor}
 * (that pool belongs to OCS indexing).
 *
 * <p>Every activator catches its own exceptions and still returns its event, so aggregator groups
 * complete; {@code groupTimeout} flushes partial groups as a backstop. A flow failure is bookkept,
 * never propagated — no CJ calls and no writes happen on customer/admin request paths here.
 */
@Configuration
@EnableIntegration
public class InventoryFlowConfig {

    private static final Logger log = LoggerFactory.getLogger(InventoryFlowConfig.class);

    private static final long GROUP_TIMEOUT_MS = 10 * 60 * 1000L;

    // ---- threading ------------------------------------------------------------------------------

    /**
     * Shared scheduler for @Scheduled crons AND Spring Integration pollers. Pool of 4: the old
     * implicit single-thread @Scheduled executor serialized the deal-lifecycle tick behind the
     * (hours-long, rate-limited) nightly CJ refresh; with 4 threads the tick keeps ticking.
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("litemall-sched-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }

    @Bean
    public TaskExecutor invflowExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20000); // a full catalog sync splits into ~10k per-pid messages
        executor.setThreadNamePrefix("invflow-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }

    // ---- channels --------------------------------------------------------------------------------

    @Bean(name = "invflow.perProduct")
    public MessageChannel invflowPerProduct(TaskExecutor invflowExecutor) {
        return new ExecutorChannel(invflowExecutor);
    }

    @Bean(name = "invflow.recheck")
    public MessageChannel invflowRecheck(InventoryFlowProperties properties) {
        return new QueueChannel(Math.max(1, properties.getRecheckQueueCapacity()));
    }

    // ---- flows -----------------------------------------------------------------------------------

    /** Catalog cycle landed: bookkeep a 'flow' run, split to per-pid events, hand to the executor. */
    @Bean
    public IntegrationFlow catalogLandedFlow(CjSyncRunRecorder runRecorder) {
        return IntegrationFlow.from("invflow.catalogLanded")
                .wireTap(tap -> tap.handle(m -> {
                    CatalogLandedSummary s = (CatalogLandedSummary) m.getPayload();
                    log.info("inventory flow: catalog landed — {} new, {} updated, {} vanished, complete={}",
                            s.inserted(), s.updated(), s.removedPids().size(), s.complete());
                }))
                .enrichHeaders(h -> h.headerFunction("invFlowRunId",
                        m -> runRecorder.open("flow")))
                .transform(CatalogLandedSummary.class, InventoryFlowConfig::toEvents)
                .split()
                .channel("invflow.perProduct")
                .get();
    }

    /** Enrichment re-promotes (cron batch item or on-demand single): UPDATED events, no run row. */
    @Bean
    public IntegrationFlow enrichedPidsFlow() {
        return IntegrationFlow.from("invflow.enrichedPids")
                .<List<String>, List<ProductFlowEvent>>transform(pids -> pids.stream()
                        .map(pid -> new ProductFlowEvent(ProductFlowEvent.Kind.UPDATED, pid))
                        .toList())
                .split()
                .channel("invflow.perProduct")
                .get();
    }

    /** Router: fan the per-pid events out by kind. */
    @Bean
    public IntegrationFlow perProductFlow() {
        return IntegrationFlow.from("invflow.perProduct")
                .<ProductFlowEvent, String>route(e -> e.kind().name(), r -> r
                        .channelMapping(ProductFlowEvent.Kind.NEW_ARRIVAL.name(), "invflow.newArrival")
                        .channelMapping(ProductFlowEvent.Kind.UPDATED.name(), "invflow.updated")
                        .channelMapping(ProductFlowEvent.Kind.VANISHED.name(), "invflow.vanished"))
                .get();
    }

    @Bean
    public IntegrationFlow newArrivalFlow(InventoryContextEnricher enricher,
                                          MarginRecorder marginRecorder,
                                          DealCandidateScorer scorer) {
        return IntegrationFlow.from("invflow.newArrival")
                .transform(ProductFlowEvent.class, enricher::enrich)
                .<ProductInventoryContext, ProductFlowEvent>transform(ctx -> {
                    marginRecorder.record(ctx);
                    return scorer.score(ctx);
                })
                .channel("invflow.processed")
                .get();
    }

    @Bean
    public IntegrationFlow updatedFlow(InventoryContextEnricher enricher,
                                       MarginRecorder marginRecorder,
                                       RetireCandidateScorer retireScorer) {
        return IntegrationFlow.from("invflow.updated")
                .transform(ProductFlowEvent.class, enricher::enrich)
                .<ProductInventoryContext, ProductFlowEvent>transform(marginRecorder::record)
                // Wave 14: hard availability problems propose retirement (off-sale) here
                .<ProductFlowEvent, ProductFlowEvent>transform(retireScorer::observe)
                .channel("invflow.processed")
                .get();
    }

    @Bean
    public IntegrationFlow vanishedFlow(AvailabilityRecorder availabilityRecorder,
                                        RetireCandidateScorer retireScorer) {
        return IntegrationFlow.from("invflow.vanished")
                .<ProductFlowEvent, ProductFlowEvent>transform(availabilityRecorder::markVanished)
                // Wave 14: the vanish that trips the streak threshold proposes retirement
                .<ProductFlowEvent, ProductFlowEvent>transform(retireScorer::observe)
                .channel("invflow.processed")
                .get();
    }

    /** Aggregator: when a split group completes, refresh the rollup + close the run row. */
    @Bean
    public IntegrationFlow processedFlow(CategoryInsightCache cache, CjSyncRunRecorder runRecorder,
                                         RetirementGovernor governor) {
        return IntegrationFlow.from("invflow.processed")
                .aggregate(a -> a
                        .groupTimeout(GROUP_TIMEOUT_MS)
                        .sendPartialResultOnExpiry(true)
                        .expireGroupsUponCompletion(true))
                .handle(message -> finishRun(message, cache, runRecorder, governor))
                .get();
    }

    /** Nightly recheck consumer: 1 msg/s poller over the bounded queue (CJ budget respected). */
    @Bean
    public IntegrationFlow recheckFlow(InventoryRecheckActivator activator) {
        return IntegrationFlow.from("invflow.recheck")
                .handle(String.class, (vid, headers) -> {
                    activator.recheck(vid);
                    return null;
                }, e -> e.poller(p -> p.fixedDelay(1000).maxMessagesPerPoll(1)))
                .get();
    }

    // ---- helpers ---------------------------------------------------------------------------------

    /** Package-visible for the router/classification unit test. */
    static List<ProductFlowEvent> toEvents(CatalogLandedSummary summary) {
        List<ProductFlowEvent> events = new ArrayList<>();
        java.util.Set<String> inserted = new java.util.HashSet<>(summary.insertedPids());
        for (String pid : summary.insertedPids()) {
            events.add(new ProductFlowEvent(ProductFlowEvent.Kind.NEW_ARRIVAL, pid));
        }
        for (String pid : summary.livePids()) {
            if (!inserted.contains(pid)) {
                events.add(new ProductFlowEvent(ProductFlowEvent.Kind.UPDATED, pid));
            }
        }
        for (String pid : summary.removedPids()) {
            events.add(new ProductFlowEvent(ProductFlowEvent.Kind.VANISHED, pid));
        }
        return events;
    }

    private static void finishRun(Message<?> message, CategoryInsightCache cache,
                                  CjSyncRunRecorder runRecorder, RetirementGovernor governor) {
        Integer runId = message.getHeaders().get("invFlowRunId", Integer.class);
        int newArrivals = 0;
        int updated = 0;
        int vanished = 0;
        Object payload = message.getPayload();
        if (payload instanceof List<?> events) {
            for (Object o : events) {
                if (o instanceof ProductFlowEvent e) {
                    switch (e.kind()) {
                        case NEW_ARRIVAL -> newArrivals++;
                        case UPDATED -> updated++;
                        case VANISHED -> vanished++;
                    }
                }
            }
        }
        // Catalog runs (runId set) force a rebuild; enrichment trickle groups are debounced.
        cache.refresh(runId != null);
        // Wave 14: only catalog runs re-measure the catalog against its target (self-guarded).
        if (runId != null) {
            governor.governAfterCatalogRun();
        }
        runRecorder.close(runId, newArrivals + updated + vanished, newArrivals, updated, vanished,
                true, null);
    }
}
