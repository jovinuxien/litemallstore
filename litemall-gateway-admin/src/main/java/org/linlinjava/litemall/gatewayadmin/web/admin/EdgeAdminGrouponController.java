package org.linlinjava.litemall.gatewayadmin.web.admin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallGroupon;
import org.linlinjava.litemall.db.domain.LitemallGrouponRules;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.db.service.LitemallGrouponRulesService;
import org.linlinjava.litemall.db.service.LitemallGrouponService;
import org.linlinjava.litemall.db.util.GrouponConstant;
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
 * Groupon (group-buy) management, ported from legacy AdminGrouponController:
 * {@code /list|create|update|delete} manage the rules, {@code /listRecord}
 * lists the running/finished groupon activities.
 *
 * <p>Deliberate difference from legacy: no in-JVM expiry task is scheduled at
 * create time (legacy used litemall-core's TaskService, banned here). Expired
 * rules are swept by the promotion service; until that lands, expiry is
 * visible via {@code expire_time} on the rule.
 */
@RestController
@RequestMapping("/srv/private/admin/groupon")
public class EdgeAdminGrouponController {

    private final LitemallGrouponRulesService rulesService;
    private final LitemallGrouponService grouponService;
    private final LitemallGoodsService goodsService;

    public EdgeAdminGrouponController(LitemallGrouponRulesService rulesService,
                                      LitemallGrouponService grouponService,
                                      LitemallGoodsService goodsService) {
        this.rulesService = rulesService;
        this.grouponService = grouponService;
        this.goodsService = goodsService;
    }

    @GetMapping("/list")
    public Mono<ApiResponse<?>> list(@RequestParam(required = false) String goodsId,
                                     @RequestParam(defaultValue = "1") Integer page,
                                     @RequestParam(defaultValue = "10") Integer limit,
                                     @RequestParam(defaultValue = "add_time") String sort,
                                     @RequestParam(defaultValue = "desc") String order) {
        return blocking(() -> AdminEdge.okList(rulesService.querySelective(
                goodsId, page, limit, AdminEdge.sort(sort, "add_time"), AdminEdge.order(order))));
    }

    @GetMapping("/listRecord")
    public Mono<ApiResponse<?>> listRecord(@RequestParam(required = false) String grouponRuleId,
                                           @RequestParam(defaultValue = "1") Integer page,
                                           @RequestParam(defaultValue = "10") Integer limit,
                                           @RequestParam(defaultValue = "add_time") String sort,
                                           @RequestParam(defaultValue = "desc") String order) {
        return blocking(() -> {
            List<LitemallGroupon> grouponList = grouponService.querySelective(
                    grouponRuleId, page, limit, AdminEdge.sort(sort, "add_time"), AdminEdge.order(order));
            List<Map<String, Object>> records = new ArrayList<>(grouponList.size());
            for (LitemallGroupon groupon : grouponList) {
                Map<String, Object> record = new HashMap<>(4);
                record.put("groupon", groupon);
                record.put("subGroupons", grouponService.queryJoinRecord(groupon.getId()));
                LitemallGrouponRules rules = rulesService.findById(groupon.getRulesId());
                record.put("rules", rules);
                record.put("goods", rules == null ? null : goodsService.findById(rules.getGoodsId()));
                records.add(record);
            }
            return AdminEdge.okList(records, grouponList);
        });
    }

    @PostMapping("/create")
    public Mono<ApiResponse<?>> create(@RequestBody LitemallGrouponRules rules) {
        return blocking(() -> {
            ApiResponse<Object> error = validate(rules);
            if (error != null) {
                return error;
            }
            LitemallGoods goods = goodsService.findById(rules.getGoodsId());
            if (goods == null) {
                return ApiResponse.fail(AdminEdge.GROUPON_GOODS_UNKNOWN, "groupon goods does not exist");
            }
            if (rulesService.countByGoodsId(rules.getGoodsId()) > 0) {
                return ApiResponse.fail(AdminEdge.GROUPON_GOODS_EXISTED, "goods already has a groupon rule");
            }
            rules.setGoodsName(goods.getName());
            rules.setPicUrl(goods.getPicUrl());
            rules.setStatus(GrouponConstant.RULE_STATUS_ON);
            rulesService.createRules(rules);
            return ApiResponse.ok(rules);
        });
    }

    @PostMapping("/update")
    public Mono<ApiResponse<?>> update(@RequestBody LitemallGrouponRules rules) {
        return blocking(() -> {
            ApiResponse<Object> error = validate(rules);
            if (error != null) {
                return error;
            }
            LitemallGrouponRules existing = rulesService.findById(rules.getId());
            if (existing == null) {
                return AdminEdge.badArgumentValue();
            }
            if (!GrouponConstant.RULE_STATUS_ON.equals(existing.getStatus())) {
                return ApiResponse.fail(AdminEdge.GROUPON_GOODS_OFFLINE, "groupon rule is already offline");
            }
            LitemallGoods goods = goodsService.findById(rules.getGoodsId());
            if (goods == null) {
                return AdminEdge.badArgumentValue();
            }
            rules.setGoodsName(goods.getName());
            rules.setPicUrl(goods.getPicUrl());
            if (rulesService.updateById(rules) == 0) {
                return AdminEdge.updateFailed();
            }
            return ApiResponse.ok(null);
        });
    }

    @PostMapping("/delete")
    public Mono<ApiResponse<?>> delete(@RequestBody LitemallGrouponRules rules) {
        return blocking(() -> {
            if (rules.getId() == null) {
                return AdminEdge.badArgument();
            }
            rulesService.delete(rules.getId());
            return ApiResponse.ok(null);
        });
    }

    private ApiResponse<Object> validate(LitemallGrouponRules rules) {
        if (rules.getGoodsId() == null || rules.getDiscount() == null
                || rules.getDiscountMember() == null || rules.getExpireTime() == null) {
            return AdminEdge.badArgument();
        }
        return null;
    }
}
