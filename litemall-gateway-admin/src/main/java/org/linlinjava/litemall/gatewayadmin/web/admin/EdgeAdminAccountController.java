package org.linlinjava.litemall.gatewayadmin.web.admin;

import java.util.List;

import org.linlinjava.litemall.db.domain.LitemallAdmin;
import org.linlinjava.litemall.db.service.LitemallAdminService;
import org.linlinjava.litemall.gatewayadmin.web.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

import static org.linlinjava.litemall.gatewayadmin.web.admin.AdminEdge.blocking;

/**
 * Admin-account management, ported from legacy AdminAdminController.
 * See {@link AdminEdge}.
 *
 * <p>Differences from legacy: password hashes are never returned to the SPA,
 * and the can't-delete-self guard (commented out in legacy after the Shiro
 * removal) is enforced using the JWT identity. Password changes go through
 * {@code /create}-time hashing only; {@code /update} ignores the password
 * field, same as legacy.
 */
@RestController
@RequestMapping("/srv/private/admin/admin")
public class EdgeAdminAccountController {

    private static final String USERNAME_PATTERN = "^[a-zA-Z0-9_-]{3,63}$";

    private final LitemallAdminService adminService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public EdgeAdminAccountController(LitemallAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/list")
    public Mono<ApiResponse<?>> list(@RequestParam(required = false) String username,
                                     @RequestParam(defaultValue = "1") Integer page,
                                     @RequestParam(defaultValue = "10") Integer limit,
                                     @RequestParam(defaultValue = "add_time") String sort,
                                     @RequestParam(defaultValue = "desc") String order) {
        return blocking(() -> {
            List<LitemallAdmin> admins = adminService.querySelective(
                    username, page, limit, AdminEdge.sort(sort, "add_time"), AdminEdge.order(order));
            admins.forEach(a -> a.setPassword(null));
            return AdminEdge.okList(admins);
        });
    }

    @GetMapping("/read")
    public Mono<ApiResponse<?>> read(@RequestParam Integer id) {
        return blocking(() -> {
            LitemallAdmin admin = adminService.findById(id);
            if (admin != null) {
                admin.setPassword(null);
            }
            return ApiResponse.ok(admin);
        });
    }

    @PostMapping("/create")
    public Mono<ApiResponse<?>> create(@RequestBody LitemallAdmin admin) {
        return blocking(() -> {
            String name = admin.getUsername();
            if (name == null || !name.matches(USERNAME_PATTERN)) {
                return ApiResponse.fail(AdminEdge.ADMIN_INVALID_NAME, "invalid administrator name");
            }
            String password = admin.getPassword();
            if (password == null || password.length() < 6) {
                return ApiResponse.fail(AdminEdge.ADMIN_INVALID_PASSWORD,
                        "administrator password must be at least 6 characters");
            }
            if (!adminService.findAdmin(name).isEmpty()) {
                return ApiResponse.fail(AdminEdge.ADMIN_NAME_EXIST, "administrator already exists");
            }
            admin.setPassword(encoder.encode(password));
            adminService.add(admin);
            admin.setPassword(null);
            return ApiResponse.ok(admin);
        });
    }

    @PostMapping("/update")
    public Mono<ApiResponse<?>> update(@RequestBody LitemallAdmin admin) {
        return blocking(() -> {
            if (admin.getId() == null) {
                return AdminEdge.badArgument();
            }
            String name = admin.getUsername();
            if (name == null || !name.matches(USERNAME_PATTERN)) {
                return ApiResponse.fail(AdminEdge.ADMIN_INVALID_NAME, "invalid administrator name");
            }
            // Password changes are not allowed through the edit endpoint (legacy rule).
            admin.setPassword(null);
            if (adminService.updateById(admin) == 0) {
                return AdminEdge.updateFailed();
            }
            return ApiResponse.ok(admin);
        });
    }

    @PostMapping("/delete")
    public Mono<ApiResponse<?>> delete(@RequestBody LitemallAdmin admin, Authentication authentication) {
        return blocking(() -> {
            Integer id = admin.getId();
            if (id == null) {
                return AdminEdge.badArgument();
            }
            if (id.equals(AdminEdge.adminId(authentication))) {
                return ApiResponse.fail(AdminEdge.ADMIN_DELETE_NOT_ALLOWED,
                        "administrators cannot delete their own account");
            }
            adminService.deleteById(id);
            return ApiResponse.ok(null);
        });
    }
}
