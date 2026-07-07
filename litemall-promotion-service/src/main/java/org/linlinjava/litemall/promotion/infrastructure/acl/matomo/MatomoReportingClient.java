package org.linlinjava.litemall.promotion.infrastructure.acl.matomo;

import org.linlinjava.litemall.promotion.infrastructure.acl.matomo.dto.MatomoUserStatRow;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Feign client over the Matomo Reporting API (Phase 3 analytics → statistics).
 * Host from {@code litemall.promotion.matomo.base-url} (profile-overridable; no
 * hardcoded host). Wrapped by {@link MatomoStatisticsAdapter} (ACL) so
 * domain/application code depends only on the {@code CustomerStatisticsProvider}
 * port, never on this client — the same boundary discipline as goods-management's
 * OCS ACL.
 *
 * <p>{@code module=API} and {@code format=json} are fixed query constants; the
 * report method, site, period, date, token and limit are passed per call.
 */
@FeignClient(name = "matomo-reporting", url = "${litemall.promotion.matomo.base-url:http://localhost}")
public interface MatomoReportingClient {

    @GetMapping("/index.php?module=API&format=json")
    List<MatomoUserStatRow> getUserReport(@RequestParam("method") String method,
                                          @RequestParam("idSite") int idSite,
                                          @RequestParam("period") String period,
                                          @RequestParam("date") String date,
                                          @RequestParam("token_auth") String tokenAuth,
                                          @RequestParam("filter_limit") int filterLimit);
}
