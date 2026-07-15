package org.linlinjava.litemall.goods.infrastructure.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on Spring's {@code @Scheduled} processing for this service. Added with
 * the flash-deal lifecycle task (Deals Phase B) — note the module had
 * {@code @Scheduled} annotations before this ({@code CjCatalogRefreshTask}'s
 * 03:00/03:30 crons) that were silently inert without it; they become live
 * from here on, which matches their documented intent (nightly CJ refresh).
 */
@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}
