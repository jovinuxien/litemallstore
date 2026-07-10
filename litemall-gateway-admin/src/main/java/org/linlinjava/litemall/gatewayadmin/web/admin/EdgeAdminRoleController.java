package org.linlinjava.litemall.gatewayadmin.web.admin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.linlinjava.litemall.db.domain.LitemallAdmin;
import org.linlinjava.litemall.db.domain.LitemallRole;
import org.linlinjava.litemall.db.service.LitemallAdminService;
import org.linlinjava.litemall.db.service.LitemallRoleService;
import org.linlinjava.litemall.gatewayadmin.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

import static org.linlinjava.litemall.gatewayadmin.web.admin.AdminEdge.blocking;

/**
 * Role management, ported from legacy AdminRoleController. See {@link AdminEdge}.
 *
 * <p>The legacy fine-grained permission endpoints ({@code /permissions}) are
 * NOT ported: they were already gutted upstream (GET returned an empty ok) and
 * nothing in the DDD services enforces per-permission checks — the admin realm
 * is a single ROLE_ADMIN today. Follow-up recorded in ROUTING.md.
 */
@RestController
@RequestMapping("/srv/private/admin/role")
public class EdgeAdminRoleController {

    private final LitemallRoleService roleService;
    private final LitemallAdminService adminService;

    public EdgeAdminRoleController(LitemallRoleService roleService,
                                   LitemallAdminService adminService) {
        this.roleService = roleService;
        this.adminService = adminService;
    }

    @GetMapping("/list")
    public Mono<ApiResponse<?>> list(@RequestParam(required = false) String name,
                                     @RequestParam(defaultValue = "1") Integer page,
                                     @RequestParam(defaultValue = "10") Integer limit,
                                     @RequestParam(defaultValue = "add_time") String sort,
                                     @RequestParam(defaultValue = "desc") String order) {
        return blocking(() -> AdminEdge.okList(roleService.querySelective(
                name, page, limit, AdminEdge.sort(sort, "add_time"), AdminEdge.order(order))));
    }

    /** {@code {value,label}} pairs for the role picker on the admin-account form. */
    @GetMapping("/options")
    public Mono<ApiResponse<?>> options() {
        return blocking(() -> {
            List<LitemallRole> roles = roleService.queryAll();
            List<Map<String, Object>> options = new ArrayList<>(roles.size());
            for (LitemallRole role : roles) {
                Map<String, Object> option = new HashMap<>(2);
                option.put("value", role.getId());
                option.put("label", role.getName());
                options.add(option);
            }
            return AdminEdge.okList(options);
        });
    }

    @GetMapping("/read")
    public Mono<ApiResponse<?>> read(@RequestParam Integer id) {
        return blocking(() -> ApiResponse.ok(roleService.findById(id)));
    }

    @PostMapping("/create")
    public Mono<ApiResponse<?>> create(@RequestBody LitemallRole role) {
        return blocking(() -> {
            if (role.getName() == null || role.getName().isEmpty()) {
                return AdminEdge.badArgument();
            }
            if (roleService.checkExist(role.getName())) {
                return ApiResponse.fail(AdminEdge.ROLE_NAME_EXIST, "role already exists");
            }
            roleService.add(role);
            return ApiResponse.ok(role);
        });
    }

    @PostMapping("/update")
    public Mono<ApiResponse<?>> update(@RequestBody LitemallRole role) {
        return blocking(() -> {
            if (role.getId() == null || role.getName() == null || role.getName().isEmpty()) {
                return AdminEdge.badArgument();
            }
            roleService.updateById(role);
            return ApiResponse.ok(null);
        });
    }

    @PostMapping("/delete")
    public Mono<ApiResponse<?>> delete(@RequestBody LitemallRole role) {
        return blocking(() -> {
            Integer id = role.getId();
            if (id == null) {
                return AdminEdge.badArgument();
            }
            for (LitemallAdmin admin : adminService.all()) {
                Integer[] roleIds = admin.getRoleIds();
                if (roleIds == null) {
                    continue;
                }
                for (Integer roleId : roleIds) {
                    if (id.equals(roleId)) {
                        return ApiResponse.fail(AdminEdge.ROLE_USER_EXIST,
                                "role is still assigned to an administrator and cannot be deleted");
                    }
                }
            }
            roleService.deleteById(id);
            return ApiResponse.ok(null);
        });
    }
}
