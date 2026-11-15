package org.linlinjava.litemall.gatewayapi.web.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

class EcbRateServiceTest {

    /** A clock the test moves by hand. */
    static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-11-14T08:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final TestClock clock = new TestClock();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile String body = EcbRatesTest.SAMPLE;
    private volatile boolean fail;

    private final Supplier<CompletableFuture<String>> fetcher = () -> {
        calls.incrementAndGet();
        return fail ? CompletableFuture.failedFuture(new IllegalStateException("ECB answered HTTP 503"))
                : CompletableFuture.completedFuture(body);
    };

    private EcbRateService service(boolean enabled) {
        return new EcbRateService(enabled, fetcher, Duration.ofHours(6), 7, clock);
    }

    @Test
    void disabledNeverFetchesAndHasNoRates() {
        EcbRateService s = service(false);
        s.onReady();
        assertNull(s.current());
        assertEquals(0, calls.get());
    }

    @Test
    void firstReadFetchesThenServesFromMemoryUntilTheRefreshInterval() {
        EcbRateService s = service(true);
        assertNotNull(s.current());
        assertNotNull(s.current());
        assertEquals(1, calls.get());

        clock.now = clock.now.plus(Duration.ofHours(5));
        s.current();
        assertEquals(1, calls.get());

        clock.now = clock.now.plus(Duration.ofHours(2));
        s.current();
        assertEquals(2, calls.get());
    }

    @Test
    void neverFetchedMeansNoRates() {
        fail = true;
        EcbRateService s = service(true);
        assertNull(s.current());
    }

    @Test
    void aFailedRefreshKeepsThePreviousCopyAndRetriesAfterTenMinutes() {
        EcbRateService s = service(true);
        assertNotNull(s.current());

        fail = true;
        clock.now = clock.now.plus(Duration.ofHours(7));
        assertNotNull(s.current(), "previous copy survives a failed refresh");
        assertEquals(2, calls.get());

        clock.now = clock.now.plus(Duration.ofMinutes(5));
        s.current();
        assertEquals(2, calls.get(), "no hammering inside the retry window");

        fail = false;
        clock.now = clock.now.plus(Duration.ofMinutes(6));
        s.current();
        assertEquals(3, calls.get());
    }

    @Test
    void anUnreadableBodyCountsAsAFailedFetch() {
        EcbRateService s = service(true);
        assertNotNull(s.current());
        body = "<html>maintenance</html>";
        clock.now = clock.now.plus(Duration.ofHours(7));
        assertNotNull(s.current());
        assertEquals(java.time.LocalDate.of(2026, 11, 13), s.current().asOf());
    }

    @Test
    void ratesOlderThanTheAllowanceAreNotServed() {
        fail = false;
        EcbRateService s = service(true);
        assertNotNull(s.current()); // fixed 2026-11-13, today 2026-11-14

        fail = true; // the ECB stays unreachable from here on
        clock.now = Instant.parse("2026-11-20T08:00:00Z"); // exactly 7 days after the fixing
        assertNotNull(s.current());
        clock.now = Instant.parse("2026-11-21T08:00:00Z"); // 8 days
        assertNull(s.current(), "a stale rate is a wrong price — show euros instead");
    }

    @Test
    void aThrowingFetcherIsContained() {
        EcbRateService s = new EcbRateService(true, () -> {
            throw new IllegalStateException("boom");
        }, Duration.ofHours(6), 7, clock);
        assertNull(s.current());
    }
}
