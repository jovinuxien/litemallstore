package org.linlinjava.litemall.goods.infrastructure.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Wave-14 auto-daily-deal knobs ({@code litemall.deals.auto-daily-*}). The pre-existing
 * lifecycle knobs under the same prefix ({@code lifecycle-enabled}, {@code tick-ms}) are
 * still read inline by {@code FlashDealLifecycleTask}'s {@code @Value}/{@code @Scheduled}
 * expressions and are deliberately not bound here.
 *
 * <p>User decision 2026-07-29 (supersedes Wave-12's manual-only rule): Today's Deals gets
 * auto-created top-N daily deals from {@code hot}-tier proposals, capped and env-killable,
 * with a hard price floor of cost × 1.05 so an auto deal can never sell at a loss.
 */
@Component
@Data
@ConfigurationProperties(prefix = "litemall.deals")
public class LitemallDealsProperties {

    /** Kill-switch: env LITEMALL_DEALS_AUTO_DAILY_ENABLED=false stops the tick entirely. */
    private boolean autoDailyEnabled = true;

    /** Max deals the morning tick creates per day. */
    private int autoDailyCap = 12;

    /** Deal window length; start = tick time. */
    private int autoDailyWindowHours = 24;

    /** Per-deal quota bound: seckill stock = min(goods stock, this). */
    private int autoDailyStockCap = 50;
}
