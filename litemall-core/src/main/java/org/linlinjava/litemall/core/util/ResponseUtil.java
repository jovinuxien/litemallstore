package org.linlinjava.litemall.core.util;

import com.github.pagehelper.Page;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *Response to operation results
 * <pre>
 * {
 *     errno: error code,
 *     errmsg: error message,
 *     data: response data
 * }
 * </pre>
 *
 * <p>
 * Error code:
 * <ul>
 * <li> 0, success;
 * <li> 4xx, front-end error, indicating that front-end developers need to re-understand the back-end interface usage specifications:
 * <ul>
 * <li> 401, parameter error, that is, the front end does not pass the parameters required by the back end;
 * <li> 402, parameter value error, that is, the parameter value passed by the front end does not meet the range received by the back end.
 * </ul>
 * <li> 5xx, backend error, except 501, indicates that backend developers should continue to optimize the code and try to avoid returning backend error codes:
 * <ul>
 * <li> 501, verification failed, that is, the backend requires the user to log in;
 * <li> 502, internal system error, that is, there is no appropriately named backend internal error;
 * <li> 503, the business is not supported, that is, although the backend defines the interface, it has not yet implemented the function;
 * <li> 504, the update data is invalid, that is, the backend adopts optimistic lock update, and there is data update failure during concurrent updates;
 * <li> 505, failed to update data, that is, the back-end database update failed (normally the update should be successful).
 * </ul>
 * <li> 6xx, small mall back-end business error code,
 * For details, see AdminResponseCode of litemall-admin-api module.
 * <li> 7xx, management background backend business error code,
 * For details, see WxResponseCode of litemall-wx-api module.
 * </ul>
 */
public class ResponseUtil {
    public static Object ok() {
        Map<String, Object> obj = new HashMap<String, Object>();
        obj.put("errno", 0);
        obj.put("errmsg", "success");
        return obj;
    }

    public static Object ok(Object data) {
        Map<String, Object> obj = new HashMap<String, Object>();
        obj.put("errno", 0);
        obj.put("errmsg", "success");
        obj.put("data", data);
        return obj;
    }

    public static Object okList(List list) {
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("list", list);

        if (list instanceof Page) {
            Page page = (Page) list;
            data.put("total", page.getTotal());
            data.put("page", page.getPageNum());
            data.put("limit", page.getPageSize());
            data.put("pages", page.getPages());
        } else {
            data.put("total", list.size());
            data.put("page", 1);
            data.put("limit", list.size());
            data.put("pages", 1);
        }

        return ok(data);
    }

    public static Object okList(List list, List pagedList) {
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("list", list);

        if (pagedList instanceof Page) {
            Page page = (Page) pagedList;
            data.put("total", page.getTotal());
            data.put("page", page.getPageNum());
            data.put("limit", page.getPageSize());
            data.put("pages", page.getPages());
        } else {
            data.put("total", pagedList.size());
            data.put("page", 1);
            data.put("limit", pagedList.size());
            data.put("pages", 1);
        }

        return ok(data);
    }

    public static Object fail() {
        Map<String, Object> obj = new HashMap<String, Object>();
        obj.put("errno", -1);
        obj.put("errmsg", "mistake");
        return obj;
    }

    public static Object fail(int errno, String errmsg) {
        Map<String, Object> obj = new HashMap<String, Object>();
        obj.put("errno", errno);
        obj.put("errmsg", errmsg);
        return obj;
    }

    public static Object fail(int errno, String errmsg, String data) {
        Map<String, Object> obj = new HashMap<String, Object>(3);
        obj.put("errno", errno);
        obj.put("errmsg", errmsg);
        obj.put("data", data);
        return obj;
    }

    public static Object badArgument() {
        return fail(401, "Wrong parameters");
    }

    public static Object badArgumentValue() {
        return fail(402, "Parameter value is wrong");
    }

    public static Object unlogin() {
        return fail(501, "Please log in");
    }

    public static Object serious() {
        return fail(502, "System internal error");
    }

    public static Object unsupport() {
        return fail(503, "Business does not support");
    }

    public static Object updatedDateExpired() {
        return fail(504, "Update data has expired");
    }

    public static Object updatedDataFailed() {
        return fail(505, "Failed to update data");
    }

    public static Object unauthz() {
        return fail(506, "No operation permission");
    }
}

