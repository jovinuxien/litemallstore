package org.linlinjava.litemall.gatewayadmin.web.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.domain.LitemallAdmin;
import org.linlinjava.litemall.db.domain.LitemallLog;
import org.linlinjava.litemall.db.service.LitemallAdminService;
import org.linlinjava.litemall.db.service.LitemallLogService;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * The audit filter must NEVER fail an admin request. The production incident
 * covered here: behind the Cloudflare/Caddy proxy chain, forwarded-header
 * processing leaves an UNRESOLVED remote InetSocketAddress (getAddress() ==
 * null), which used to NPE on every POST/PUT/DELETE under /srv/private/admin/**
 * before routing — surfacing as "request failed (500)" on deal create, comment
 * reply, retire approve/dismiss, DIY page save, ad delete.
 *
 * <p>Plain subclass fakes instead of Mockito: the module's Mockito version
 * cannot instrument classes on this JDK.
 */
class AdminAuditLogFilterTest {

    /** Captures the async fire-and-forget audit write. */
    private static final class RecordingLogService extends LitemallLogService {
        final AtomicReference<LitemallLog> last = new AtomicReference<>();
        final CountDownLatch written = new CountDownLatch(1);

        @Override
        public void add(LitemallLog log) {
            last.set(log);
            written.countDown();
        }
    }

    private static final class NoAdminService extends LitemallAdminService {
        @Override
        public LitemallAdmin findById(Integer id) {
            return null;
        }
    }

    private static WebFilterChain okChain() {
        return exchange -> Mono.empty();
    }

    private static LitemallLog runFilter(MockServerHttpRequest request) throws InterruptedException {
        RecordingLogService logService = new RecordingLogService();
        AdminAuditLogFilter filter = new AdminAuditLogFilter(logService, new NoAdminService());
        filter.filter(MockServerWebExchange.from(request), okChain()).block();
        assertTrue(logService.written.await(2, TimeUnit.SECONDS), "audit row was never written");
        return logService.last.get();
    }

    @Test
    void unresolvedRemoteAddressDoesNotFailTheRequestAndLogsTheHostString() throws InterruptedException {
        LitemallLog row = runFilter(MockServerHttpRequest
                .post("/srv/private/admin/deal/create")
                .remoteAddress(InetSocketAddress.createUnresolved("203.0.113.9", 443))
                .build());
        assertEquals("203.0.113.9", row.getIp());
        assertEquals("POST /srv/private/admin/deal/create", row.getAction());
    }

    @Test
    void forwardedForHeaderWinsWhenPresent() throws InterruptedException {
        LitemallLog row = runFilter(MockServerHttpRequest
                .post("/srv/private/admin/comment/reply")
                .header("X-Forwarded-For", "198.51.100.7, 172.18.0.2")
                .remoteAddress(InetSocketAddress.createUnresolved("172.18.0.2", 443))
                .build());
        assertEquals("198.51.100.7", row.getIp());
    }

    @Test
    void resolvedRemoteAddressStillLogsItsHostAddress() throws InterruptedException {
        LitemallLog row = runFilter(MockServerHttpRequest
                .post("/srv/private/admin/ad/delete")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 55555))
                .build());
        assertEquals("127.0.0.1", row.getIp());
    }
}
