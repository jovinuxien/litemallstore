package org.linlinjava.litemall.goods.interfaces.rest;


import jakarta.validation.constraints.NotNull;
import org.linlinjava.litemall.core.validator.Order;
import org.linlinjava.litemall.core.validator.Sort;
import org.linlinjava.litemall.goods.application.LitemallUserManagementService;
import org.linlinjava.litemall.goods.domain.model.agregates.user.LitemallUserAggregate;
import org.linlinjava.litemall.goods.domain.model.valueobjects.user.LitemallUserId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/srv/account")
public class LitemallAccountController {

    @Autowired
    private LitemallUserManagementService userManagementService;

    @GetMapping("/list")
    public Object list(String username, String mobile,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer limit,
                       @Sort @RequestParam(defaultValue = "add_time") String sort,
                       @Order @RequestParam(defaultValue = "desc") String order) {
        return userManagementService.listUsers(username, mobile, page, limit, sort, order);
    }

    @GetMapping("/detail")
    public Object userDetail(@NotNull Integer id) {
        return  userManagementService.userDetail(id);
    }

    @GetMapping("/detailByUsername")
    public Object userDetailByUsername(@NotNull String username) {
        return  userManagementService.userDetailByUsername(username);
    }


    @PostMapping("/create")
    public Object createUser(@NotNull Integer id) {
        LitemallUserAggregate userAggregate = new LitemallUserAggregate();
        userAggregate.setUserId(new LitemallUserId(id));
        return  userManagementService.saveUser(userAggregate);
    }


}
