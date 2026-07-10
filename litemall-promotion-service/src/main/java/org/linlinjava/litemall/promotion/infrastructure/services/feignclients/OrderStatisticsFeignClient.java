package org.linlinjava.litemall.promotion.infrastructure.services.feignclients;

import org.linlinjava.litemall.promotion.domain.model.valueobjects.ApiResponse;
import org.linlinjava.litemall.promotion.infrastructure.services.feignclients.dto.CustomerRfmStatDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Feign read path to litemall-order's customer RFM statistics endpoint. Host
 * from {@code order.service.url} (profile-overridable; no hardcoded host). Wrapped
 * by {@code OrderStatisticsAdapter} (ACL) so domain/application code depends only
 * on the {@code CustomerStatisticsProvider} port, never on this client.
 *
 * <p><b>Agreed contract / follow-up:</b> {@code GET /srv/order/admin/stat/customer-rfm?since=<ISO-8601>}
 * returning {@code ApiResponse<List<CustomerRfmStatDto>>}. If litemall-order does
 * not yet expose it, that is an {@code order}-worktree follow-up.
 */
@FeignClient(name = "order-statistics", url = "${order.service.url}")
public interface OrderStatisticsFeignClient {

    @GetMapping("/srv/order/admin/stat/customer-rfm")
    ApiResponse<List<CustomerRfmStatDto>> getCustomerRfm(@RequestParam("since") String sinceIso);
}
