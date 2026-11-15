package org.linlinjava.litemall.gatewayapi.web.currency;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Keeps the last good copy of the ECB's daily euro reference rates in memory
 * for the storefront's DISPLAY currency (the store still charges EUR — these
 * rates never touch the money path).
 *
 * <p><b>No scheduler.</b> The copy is refreshed on boot and then lazily: a
 * read that finds it older than {@code refresh-hours} starts ONE background
 * fetch and answers with what it has. A failed fetch keeps the previous copy
 * and is retried after {@link #RETRY_AFTER}.
 *
 * <p><b>Fail honest.</b> {@link #current()} answers null — and the storefront
 * shows euros — when there has never been a successful fetch or when the
 * ECB's own date on the copy is more than {@code max-age-days} old. A stale
 * rate is a wrong price; no rate is just the euro price we charge anyway.
 * (The ECB publishes on TARGET working days only, so the allowance must
 * cover a long weekend.)
 */
@Component
public class EcbRateService {

    private static final Logger log = LoggerFactory.getLogger(EcbRateService.class);
    static final Duration RETRY_AFTER = Duration.ofMinutes(10);

    private final boolean enabled;
    private final Duration refreshEvery;
    private final int maxAgeDays;
    private final Supplier<CompletableFuture<String>> fetcher;
    private final Clock clock;

    private volatile EcbRates rates;
    private volatile Instant nextAttempt = Instant.MIN;
    private final AtomicBoolean inFlight = new AtomicBoolean();

    @Autowired
    public EcbRateService(@Value("${litemall.currency.display:EUR}") String display,
                          @Value("${litemall.currency.ecb-url:https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml}") String url,
                          @Value("${litemall.currency.refresh-hours:6}") int refreshHours,
                          @Value("${litemall.currency.max-age-days:7}") int maxAgeDays) {
        this(CurrencyController.parseCurrencies(display).size() > 1, httpFetcher(url),
                Duration.ofHours(Math.max(1, refreshHours)), maxAgeDays, Clock.systemUTC());
    }

    EcbRateService(boolean enabled, Supplier<CompletableFuture<String>> fetcher, Duration refreshEvery, int maxAgeDays, Clock clock) {
        this.enabled = enabled;
        this.fetcher = fetcher;
        this.refreshEvery = refreshEvery;
        this.maxAgeDays = Math.max(1, maxAgeDays);
        this.clock = clock;
    }

    private static Supplier<CompletableFuture<String>> httpFetcher(String url) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        return () -> http.sendAsync(
                        HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                                .header("User-Agent", "storefront-rates/1.0").GET().build(),
                        HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> {
                    if (res.statusCode() != 200) {
                        throw new IllegalStateException("ECB answered HTTP " + res.statusCode());
                    }
                    return res.body();
                });
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (enabled) {
            log.info("Display currency: ENABLED — fetching ECB reference rates (refresh {} h, max age {} d)",
                    refreshEvery.toHours(), maxAgeDays);
            refreshIfDue();
        } else {
            log.info("Display currency: OFF (only EUR listed) — no ECB fetch");
        }
    }

    /** The usable rates, or null when none are fresh enough to show a price with. */
    public EcbRates current() {
        if (!enabled) {
            return null;
        }
        refreshIfDue();
        EcbRates r = rates;
        if (r == null) {
            return null;
        }
        return r.asOf().isBefore(LocalDate.now(clock).minusDays(maxAgeDays)) ? null : r;
    }

    /** Starts at most one background fetch when the copy is due. Never blocks, never throws. */
    CompletableFuture<Void> refreshIfDue() {
        Instant now = clock.instant();
        if (!enabled || now.isBefore(nextAttempt) || !inFlight.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<String> call;
        try {
            call = fetcher.get();
        } catch (RuntimeException e) {
            call = CompletableFuture.failedFuture(e);
        }
        return call.thenApply(EcbRates::parse).handle((fresh, err) -> {
            if (err == null) {
                rates = fresh;
                nextAttempt = clock.instant().plus(refreshEvery);
                log.info("ECB reference rates loaded, fixed {}", fresh.asOf());
            } else {
                nextAttempt = clock.instant().plus(RETRY_AFTER);
                log.warn("ECB rate fetch failed ({}); keeping the copy fixed {}", err.getMessage(),
                        rates == null ? "never" : rates.asOf());
            }
            inFlight.set(false);
            return null;
        });
    }
}
