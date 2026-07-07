package org.linlinjava.litemall.gatewayadmin.web.admin;

import org.linlinjava.litemall.db.domain.LitemallAd;
import org.linlinjava.litemall.db.service.LitemallAdService;
import org.linlinjava.litemall.gatewayadmin.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

import static org.linlinjava.litemall.gatewayadmin.web.admin.AdminEdge.blocking;

/** Advertisement management, ported from legacy AdminAdController. See {@link AdminEdge}. */
@RestController
@RequestMapping("/srv/private/admin/ad")
public class EdgeAdminAdController {

    private final LitemallAdService adService;

    public EdgeAdminAdController(LitemallAdService adService) {
        this.adService = adService;
    }

    @GetMapping("/list")
    public Mono<ApiResponse<?>> list(@RequestParam(required = false) String name,
                                     @RequestParam(required = false) String content,
                                     @RequestParam(defaultValue = "1") Integer page,
                                     @RequestParam(defaultValue = "10") Integer limit,
                                     @RequestParam(defaultValue = "add_time") String sort,
                                     @RequestParam(defaultValue = "desc") String order) {
        return blocking(() -> AdminEdge.okList(adService.querySelective(
                name, content, page, limit, AdminEdge.sort(sort, "add_time"), AdminEdge.order(order))));
    }

    @GetMapping("/read")
    public Mono<ApiResponse<?>> read(@RequestParam Integer id) {
        return blocking(() -> ApiResponse.ok(adService.findById(id)));
    }

    @PostMapping("/create")
    public Mono<ApiResponse<?>> create(@RequestBody LitemallAd ad) {
        return blocking(() -> {
            ApiResponse<Object> error = validate(ad);
            if (error != null) {
                return error;
            }
            adService.add(ad);
            return ApiResponse.ok(ad);
        });
    }

    @PostMapping("/update")
    public Mono<ApiResponse<?>> update(@RequestBody LitemallAd ad) {
        return blocking(() -> {
            ApiResponse<Object> error = validate(ad);
            if (error != null) {
                return error;
            }
            if (adService.updateById(ad) == 0) {
                return AdminEdge.updateFailed();
            }
            return ApiResponse.ok(ad);
        });
    }

    @PostMapping("/delete")
    public Mono<ApiResponse<?>> delete(@RequestBody LitemallAd ad) {
        return blocking(() -> {
            if (ad.getId() == null) {
                return AdminEdge.badArgument();
            }
            adService.deleteById(ad.getId());
            return ApiResponse.ok(null);
        });
    }

    private ApiResponse<Object> validate(LitemallAd ad) {
        if (ad.getName() == null || ad.getName().isEmpty()
                || ad.getContent() == null || ad.getContent().isEmpty()) {
            return AdminEdge.badArgument();
        }
        return null;
    }
}
