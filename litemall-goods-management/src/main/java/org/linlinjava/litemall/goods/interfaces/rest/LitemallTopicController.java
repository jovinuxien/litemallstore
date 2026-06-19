package org.linlinjava.litemall.goods.interfaces.rest;

import jakarta.validation.constraints.NotNull;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.core.validator.Order;
import org.linlinjava.litemall.core.validator.Sort;
import org.linlinjava.litemall.goods.application.topic.TopicQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Anonymous customer topic/article surface (litemall-wx-api {@code /wx/topic} parity), on
 * {@code /srv/topic}. Public per {@code litemall.svcsecurity.public-paths}.
 */
@RestController
@RequestMapping("/srv/topic")
public class LitemallTopicController {

    private final TopicQueryService topicQueryService;

    public LitemallTopicController(TopicQueryService topicQueryService) {
        this.topicQueryService = topicQueryService;
    }

    @GetMapping("/list")
    public Object list(@RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer limit,
                       @Sort @RequestParam(defaultValue = "add_time") String sort,
                       @Order @RequestParam(defaultValue = "desc") String order) {
        return ResponseUtil.okList(topicQueryService.list(page, limit, sort, order));
    }

    @GetMapping("/detail")
    public Object detail(@NotNull Integer id) {
        Map<String, Object> entity = topicQueryService.detail(id);
        if (entity == null) {
            return ResponseUtil.badArgumentValue();
        }
        return ResponseUtil.ok(entity);
    }

    @GetMapping("/related")
    public Object related(@NotNull Integer id) {
        return ResponseUtil.okList(topicQueryService.related(id));
    }
}
