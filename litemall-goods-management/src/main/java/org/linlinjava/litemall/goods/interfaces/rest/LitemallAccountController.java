package org.linlinjava.litemall.goods.interfaces.rest;


import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.core.validator.Order;
import org.linlinjava.litemall.core.validator.Sort;
import org.linlinjava.litemall.db.domain.LitemallUser;
import org.linlinjava.litemall.goods.application.LitemallUserManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.NotNull;
import java.util.List;

@RestController
@RequestMapping("/srv")
public class LitemallAccountController {

    @Autowired
    private LitemallUserManagementService userManagementService;

    @GetMapping("/account/list")
    public Object list(String username, String mobile,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer limit,
                       @Sort @RequestParam(defaultValue = "add_time") String sort,
                       @Order @RequestParam(defaultValue = "desc") String order) {
        return userManagementService.listUsers(username, mobile, page, limit, sort, order);
    }

    @GetMapping("account/detail")
    public Object userDetail(@NotNull Integer id) {
        return  userManagementService.userDetail(id);
    }
}
