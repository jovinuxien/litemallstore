package org.linlinjava.litemall.goods.interfaces.rest.admin;

import jakarta.validation.constraints.NotNull;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.core.validator.Order;
import org.linlinjava.litemall.core.validator.Sort;
import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.db.service.LitemallUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin user (customer) management, ported from litemall-admin-api
 * ({@code admin.web.AdminUserController}).
 * Mounted under {@code /srv/private/admin/**} (ROLE_ADMIN-gated by litemall-svcsecurity).
 */
@RestController
@RequestMapping("/srv/private/admin/user")
@Validated
public class AdminUserController {
    private final Log logger = LogFactory.getLog(AdminUserController.class);

    @Autowired
    private LitemallUserService userService;

    @GetMapping("/list")
    public Object list(String username, String mobile,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer limit,
                       @Sort @RequestParam(defaultValue = "add_time") String sort,
                       @Order @RequestParam(defaultValue = "desc") String order) {
        List<LitemallUser> userList = userService.querySelective(username, mobile, page, limit, sort, order);
        userList.forEach(AdminUserController::scrub);
        return ResponseUtil.okList(userList);
    }

    @GetMapping("/detail")
    public Object userDetail(@NotNull Integer id) {
        LitemallUser user = userService.findById(id);
        return ResponseUtil.ok(scrub(user));
    }

    /** Upstream serialized the whole row — never ship the password hash or wx session key. */
    private static LitemallUser scrub(LitemallUser user) {
        if (user != null) {
            user.setPassword(null);
            user.setSessionKey(null);
        }
        return user;
    }

    @PostMapping("/update")
    public Object userUpdate(@RequestBody LitemallUser user) {
        if (user.getId() == null) {
            return ResponseUtil.badArgument();
        }
        return ResponseUtil.ok(userService.updateById(user));
    }
}
