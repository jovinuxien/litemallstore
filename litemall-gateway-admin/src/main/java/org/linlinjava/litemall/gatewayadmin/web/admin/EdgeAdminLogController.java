package org.linlinjava.litemall.gatewayadmin.web.admin;

import org.linlinjava.litemall.db.service.LitemallLogService;
import org.linlinjava.litemall.gatewayadmin.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

import static org.linlinjava.litemall.gatewayadmin.web.admin.AdminEdge.blocking;

/**
 * Operation-log viewer (read-only), ported from legacy AdminLogController.
 * New rows are produced by {@code AdminAuditLogFilter} on every admin mutation
 * through this gateway. See {@link AdminEdge}.
 */
@RestController
@RequestMapping("/srv/private/admin/log")
public class EdgeAdminLogController {

    private final LitemallLogService logService;

    public EdgeAdminLogController(LitemallLogService logService) {
        this.logService = logService;
    }

    @GetMapping("/list")
    public Mono<ApiResponse<?>> list(@RequestParam(required = false) String name,
                                     @RequestParam(defaultValue = "1") Integer page,
                                     @RequestParam(defaultValue = "10") Integer limit,
                                     @RequestParam(defaultValue = "add_time") String sort,
                                     @RequestParam(defaultValue = "desc") String order) {
        return blocking(() -> AdminEdge.okList(logService.querySelective(
                name, page, limit, AdminEdge.sort(sort, "add_time"), AdminEdge.order(order))));
    }
}
