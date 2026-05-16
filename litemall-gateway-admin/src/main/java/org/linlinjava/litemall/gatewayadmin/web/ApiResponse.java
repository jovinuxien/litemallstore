package org.linlinjava.litemall.gatewayadmin.web;

import java.util.HashMap;
import java.util.Map;

/**
 * litemall response envelope {@code {errno, errmsg, data}} kept for the admin
 * SPA. Reimplemented here (instead of litemall-core's ResponseUtil) because
 * litemall-core drags servlet Spring MVC into this reactive gateway.
 */
public final class ApiResponse {

    private ApiResponse() {
    }

    public static Map<String, Object> ok(Object data) {
        Map<String, Object> m = new HashMap<>();
        m.put("errno", 0);
        m.put("errmsg", "成功");
        m.put("data", data);
        return m;
    }

    public static Map<String, Object> fail(int errno, String errmsg) {
        Map<String, Object> m = new HashMap<>();
        m.put("errno", errno);
        m.put("errmsg", errmsg);
        return m;
    }
}
