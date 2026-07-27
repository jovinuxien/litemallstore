package org.linlinjava.litemall.goods.infrastructure.configuration;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.goods.application.inventoryflow.CatalogLandedSummary;
import org.linlinjava.litemall.goods.application.inventoryflow.ProductFlowEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Router-input classification (the flow's split step): inserted pids → NEW_ARRIVAL,
 * live-minus-inserted → UPDATED, removed → VANISHED — each pid exactly once.
 */
public class InventoryFlowEventsTest {

    @Test
    public void classifiesEveryPidExactlyOnce() {
        CatalogLandedSummary summary = new CatalogLandedSummary(
                List.of("n1", "n2"),
                List.of("gone1"),
                Set.of("n1", "n2", "u1", "u2", "u3"),
                5, 2, 3, true);

        List<ProductFlowEvent> events = InventoryFlowConfig.toEvents(summary);

        Map<ProductFlowEvent.Kind, Set<String>> byKind = events.stream().collect(
                Collectors.groupingBy(ProductFlowEvent::kind,
                        Collectors.mapping(ProductFlowEvent::pid, Collectors.toSet())));
        assertEquals(Set.of("n1", "n2"), byKind.get(ProductFlowEvent.Kind.NEW_ARRIVAL));
        assertEquals(Set.of("u1", "u2", "u3"), byKind.get(ProductFlowEvent.Kind.UPDATED));
        assertEquals(Set.of("gone1"), byKind.get(ProductFlowEvent.Kind.VANISHED));
        assertEquals(6, events.size(), "no pid may be classified twice");
    }

    @Test
    public void emptySummaryYieldsNoEvents() {
        CatalogLandedSummary summary = new CatalogLandedSummary(
                List.of(), List.of(), Set.of(), 0, 0, 0, false);
        assertTrue(InventoryFlowConfig.toEvents(summary).isEmpty());
    }
}
