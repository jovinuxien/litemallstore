package org.linlinjava.litemall.gatewayadmin.web.admin;

import org.linlinjava.litemall.db.domain.LitemallCoupon;
import org.linlinjava.litemall.db.service.LitemallCouponService;
import org.linlinjava.litemall.db.service.LitemallCouponUserService;
import org.linlinjava.litemall.db.util.CouponConstant;
import org.linlinjava.litemall.gatewayadmin.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

import static org.linlinjava.litemall.gatewayadmin.web.admin.AdminEdge.blocking;

/** Coupon management, ported from legacy AdminCouponController. See {@link AdminEdge}. */
@RestController
@RequestMapping("/srv/private/admin/coupon")
public class EdgeAdminCouponController {

    private final LitemallCouponService couponService;
    private final LitemallCouponUserService couponUserService;

    public EdgeAdminCouponController(LitemallCouponService couponService,
                                     LitemallCouponUserService couponUserService) {
        this.couponService = couponService;
        this.couponUserService = couponUserService;
    }

    @GetMapping("/list")
    public Mono<ApiResponse<?>> list(@RequestParam(required = false) String name,
                                     @RequestParam(required = false) Short type,
                                     @RequestParam(required = false) Short status,
                                     @RequestParam(defaultValue = "1") Integer page,
                                     @RequestParam(defaultValue = "10") Integer limit,
                                     @RequestParam(defaultValue = "add_time") String sort,
                                     @RequestParam(defaultValue = "desc") String order) {
        return blocking(() -> AdminEdge.okList(couponService.querySelective(
                name, type, status, page, limit, AdminEdge.sort(sort, "add_time"), AdminEdge.order(order))));
    }

    /** Issued coupons (per-user instances) — the "issued" tab of the coupon screen. */
    @GetMapping("/listuser")
    public Mono<ApiResponse<?>> listUser(@RequestParam(required = false) Integer userId,
                                         @RequestParam(required = false) Integer couponId,
                                         @RequestParam(required = false) Short status,
                                         @RequestParam(defaultValue = "1") Integer page,
                                         @RequestParam(defaultValue = "10") Integer limit,
                                         @RequestParam(defaultValue = "add_time") String sort,
                                         @RequestParam(defaultValue = "desc") String order) {
        return blocking(() -> AdminEdge.okList(couponUserService.queryList(
                userId, couponId, status, page, limit, AdminEdge.sort(sort, "add_time"), AdminEdge.order(order))));
    }

    @GetMapping("/read")
    public Mono<ApiResponse<?>> read(@RequestParam Integer id) {
        return blocking(() -> ApiResponse.ok(couponService.findById(id)));
    }

    @PostMapping("/create")
    public Mono<ApiResponse<?>> create(@RequestBody LitemallCoupon coupon) {
        return blocking(() -> {
            if (coupon.getName() == null || coupon.getName().isEmpty()) {
                return AdminEdge.badArgument();
            }
            if (CouponConstant.TYPE_CODE.equals(coupon.getType())) {
                coupon.setCode(couponService.generateCode());
            }
            couponService.add(coupon);
            return ApiResponse.ok(coupon);
        });
    }

    @PostMapping("/update")
    public Mono<ApiResponse<?>> update(@RequestBody LitemallCoupon coupon) {
        return blocking(() -> {
            if (coupon.getName() == null || coupon.getName().isEmpty()) {
                return AdminEdge.badArgument();
            }
            if (couponService.updateById(coupon) == 0) {
                return AdminEdge.updateFailed();
            }
            return ApiResponse.ok(coupon);
        });
    }

    @PostMapping("/delete")
    public Mono<ApiResponse<?>> delete(@RequestBody LitemallCoupon coupon) {
        return blocking(() -> {
            if (coupon.getId() == null) {
                return AdminEdge.badArgument();
            }
            couponService.deleteById(coupon.getId());
            return ApiResponse.ok(null);
        });
    }
}
