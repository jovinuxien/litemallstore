package org.linlinjava.litemall.goods.interfaces.rest.admin;

import jakarta.validation.constraints.NotNull;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.linlinjava.litemall.core.util.JacksonUtil;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.core.validator.Order;
import org.linlinjava.litemall.core.validator.Sort;
import org.linlinjava.litemall.db.domain.LitemallTopic;
import org.linlinjava.litemall.db.service.LitemallTopicService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin topic (专题) management, ported from litemall-admin-api ({@code admin.web.AdminTopicController}).
 * Mounted under {@code /srv/private/admin/**} (ROLE_ADMIN-gated by litemall-svcsecurity).
 */
@RestController
@RequestMapping("/srv/private/admin/topic")
@Validated
public class AdminTopicController {
    private final Log logger = LogFactory.getLog(AdminTopicController.class);

    @Autowired
    private LitemallTopicService topicService;

    @GetMapping("/list")
    public Object list(String title, String subtitle,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer limit,
                       @Sort(accepts = {"id", "add_time", "price"}) @RequestParam(defaultValue = "add_time") String sort,
                       @Order @RequestParam(defaultValue = "desc") String order) {
        List<LitemallTopic> topicList = topicService.querySelective(title, subtitle, page, limit, sort, order);
        return ResponseUtil.okList(topicList);
    }

    @PostMapping("/create")
    public Object create(@RequestBody LitemallTopic topic) {
        if (StringUtils.isEmpty(topic.getTitle())) {
            return ResponseUtil.badArgument();
        }
        topicService.add(topic);
        return ResponseUtil.ok(topic);
    }

    @GetMapping("/read")
    public Object read(@NotNull Integer id) {
        LitemallTopic topic = topicService.findById(id);
        if (topic == null) {
            return ResponseUtil.badArgumentValue();
        }
        return ResponseUtil.ok(topic);
    }

    @PostMapping("/update")
    public Object update(@RequestBody LitemallTopic topic) {
        if (topic.getId() == null) {
            return ResponseUtil.badArgument();
        }
        if (topicService.updateById(topic) == 0) {
            return ResponseUtil.updatedDataFailed();
        }
        return ResponseUtil.ok(topic);
    }

    @PostMapping("/delete")
    public Object delete(@RequestBody LitemallTopic topic) {
        Integer id = topic.getId();
        if (id == null) {
            return ResponseUtil.badArgument();
        }
        topicService.deleteById(id);
        return ResponseUtil.ok();
    }

    /** Accepts {@code {"ids":[1,2]}} — same binding as the upstream admin-api batchDelete. */
    @PostMapping("/batch-delete")
    public Object batchDelete(@RequestBody String body) {
        List<Integer> ids = JacksonUtil.parseIntegerList(body, "ids");
        if (ids == null || ids.isEmpty()) {
            return ResponseUtil.badArgument();
        }
        topicService.deleteByIds(ids);
        return ResponseUtil.ok();
    }
}
