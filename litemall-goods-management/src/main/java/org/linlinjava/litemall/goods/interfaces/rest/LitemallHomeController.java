package org.linlinjava.litemall.goods.interfaces.rest;

import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.goods.infrastructure.configuration.MallInfoProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Anonymous storefront metadata (litemall-wx-api {@code /wx/home/about} parity), on
 * {@code /srv/home}. Public per {@code litemall.svcsecurity.public-paths}. The aggregated home
 * index itself is already served by {@code LitemallGoodsController#index} ({@code /srv/goods/index}).
 */
@RestController
@RequestMapping("/srv/home")
public class LitemallHomeController {

    private final MallInfoProperties mallInfo;

    public LitemallHomeController(MallInfoProperties mallInfo) {
        this.mallInfo = mallInfo;
    }

    @GetMapping("/about")
    public Object about() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", mallInfo.getName());
        data.put("address", mallInfo.getAddress());
        data.put("phone", mallInfo.getPhone());
        data.put("qq", mallInfo.getQq());
        data.put("longitude", mallInfo.getLongitude());
        data.put("latitude", mallInfo.getLatitude());
        return ResponseUtil.ok(data);
    }
}
