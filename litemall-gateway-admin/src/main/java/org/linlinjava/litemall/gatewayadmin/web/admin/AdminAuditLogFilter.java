package org.linlinjava.litemall.gatewayadmin.web.admin;

import java.net.InetSocketAddress;
import java.util.List;

import org.linlinjava.litemall.db.domain.LitemallAdmin;
import org.linlinjava.litemall.db.domain.LitemallLog;
import org.linlinjava.litemall.db.service.LitemallAdminService;
import org.linlinjava.litemall.db.service.LitemallLogService;
import org.linlinjava.litemall.gatewayadmin.security.IdentityForwardingFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Writes an operation-log row ({@code litemall_log}) for every admin mutation
 * that passes through this gateway — both the edge-hosted CRUD controllers and
 * the {@code /srv/private/admin/**} surface proxied to goods-management/order.
 *
 * <p>The legacy admin-api populated this table via LogHelper inside each
 * controller; in the DDD split no service writes it, so the admin "operation
 * log" screen would stay empty forever. Auditing at the edge covers every
 * admin-realm mutation in one place.
 *
 * <p>Runs after {@link IdentityForwardingFilter} (which stamps the validated
 * {@code X-User-Id}) and before routing. The write itself is fire-and-forget
 * on boundedElastic; a logging failure never fails the request.
 */
@Component
public class AdminAuditLogFilter implements WebFilter, Ordered {

    /** Matches legacy LogHelper's LOG_TYPE_GENERAL. */
    private static final int LOG_TYPE_GENERAL = 1;

    private static final Logger log = LoggerFactory.getLogger(AdminAuditLogFilter.class);

    private final LitemallLogService logService;
    private final LitemallAdminService adminService;

    public AdminAuditLogFilter(LitemallLogService logService, LitemallAdminService adminService) {
        this.logService = logService;
        this.adminService = adminService;
    }

    @Override
    public int getOrder() {
        // After IdentityForwardingFilter (HIGHEST + 100) so X-User-Id is present.
        return Ordered.HIGHEST_PRECEDENCE + 200;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (!isAuditedMutation(request)) {
            return chain.filter(exchange);
        }
        // Affiliate principals (typ=affiliate, Wave 5) never audit here: their
        // X-User-Id is a litemall_user id and resolveAdminName would write a
        // wrong-identity litemall_admin row. (They only reach this prefix as
        // 403s anyway — SecurityConfig runs after this filter.)
        if ("affiliate".equals(request.getHeaders().getFirst(IdentityForwardingFilter.HDR_USER_TYPE))) {
            return chain.filter(exchange);
        }
        String adminId = request.getHeaders().getFirst(IdentityForwardingFilter.HDR_USER_ID);
        String action = request.getMethod() + " " + request.getURI().getPath();
        String ip = clientIp(request);
        return chain.filter(exchange).doFinally(signal ->
                Mono.fromRunnable(() -> record(adminId, action, ip, exchange.getResponse().getStatusCode()))
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe(v -> { }, e -> log.warn("audit log write failed for {}", action, e)));
    }

    private static boolean isAuditedMutation(ServerHttpRequest request) {
        HttpMethod method = request.getMethod();
        boolean mutating = HttpMethod.POST.equals(method) || HttpMethod.PUT.equals(method)
                || HttpMethod.DELETE.equals(method);
        return mutating && request.getURI().getPath().startsWith("/srv/private/admin/");
    }

    private void record(String adminId, String action, String ip, HttpStatusCode status) {
        LitemallLog entry = new LitemallLog();
        entry.setAdmin(resolveAdminName(adminId));
        entry.setIp(ip);
        entry.setType(LOG_TYPE_GENERAL);
        entry.setAction(action);
        entry.setStatus(status != null && status.is2xxSuccessful());
        entry.setResult(status == null ? "unknown" : String.valueOf(status.value()));
        logService.add(entry);
    }

    private String resolveAdminName(String adminId) {
        if (adminId == null) {
            return "anonymous";
        }
        try {
            LitemallAdmin admin = adminService.findById(Integer.valueOf(adminId));
            return admin != null ? admin.getUsername() : adminId;
        } catch (RuntimeException e) {
            return adminId;
        }
    }

    private static String clientIp(ServerHttpRequest request) {
        // Cloudflare fronts the edge: CF-Connecting-IP is the real visitor
        // address. X-Forwarded-For / remoteAddress only see the CF proxy hop
        // (and Spring's forwarded-header processing consumes XFF anyway).
        String cfIp = request.getHeaders().getFirst("CF-Connecting-IP");
        if (cfIp != null && !cfIp.isBlank()) {
            return cfIp.trim();
        }
        List<String> forwarded = request.getHeaders().get("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.get(0).split(",")[0].trim();
        }
        InetSocketAddress remote = request.getRemoteAddress();
        if (remote == null) {
            return "unknown";
        }
        // Behind the proxy chain, forwarded-header processing consumes
        // X-Forwarded-For and replaces the remote address with an UNRESOLVED
        // InetSocketAddress: getAddress() is null, but getHostString() still
        // carries the client IP literal.
        return remote.getAddress() != null ? remote.getAddress().getHostAddress() : remote.getHostString();
    }
}
