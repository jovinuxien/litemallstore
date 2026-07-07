package org.linlinjava.litemall.gatewayadmin.web.admin;

import java.util.HashMap;
import java.util.Map;

import org.linlinjava.litemall.db.domain.LitemallAdmin;
import org.linlinjava.litemall.db.domain.LitemallNotice;
import org.linlinjava.litemall.db.domain.LitemallNoticeAdmin;
import org.linlinjava.litemall.db.service.LitemallAdminService;
import org.linlinjava.litemall.db.service.LitemallNoticeAdminService;
import org.linlinjava.litemall.db.service.LitemallNoticeService;
import org.linlinjava.litemall.gatewayadmin.web.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

import static org.linlinjava.litemall.gatewayadmin.web.admin.AdminEdge.blocking;

/**
 * Notice management, ported from legacy AdminNoticeController: a notice is
 * created once and fanned out to a per-admin read-state row for every admin
 * account. See {@link AdminEdge}.
 */
@RestController
@RequestMapping("/srv/private/admin/notice")
public class EdgeAdminNoticeController {

    private final LitemallNoticeService noticeService;
    private final LitemallNoticeAdminService noticeAdminService;
    private final LitemallAdminService adminService;

    public EdgeAdminNoticeController(LitemallNoticeService noticeService,
                                     LitemallNoticeAdminService noticeAdminService,
                                     LitemallAdminService adminService) {
        this.noticeService = noticeService;
        this.noticeAdminService = noticeAdminService;
        this.adminService = adminService;
    }

    @GetMapping("/list")
    public Mono<ApiResponse<?>> list(@RequestParam(required = false) String title,
                                     @RequestParam(required = false) String content,
                                     @RequestParam(defaultValue = "1") Integer page,
                                     @RequestParam(defaultValue = "10") Integer limit,
                                     @RequestParam(defaultValue = "add_time") String sort,
                                     @RequestParam(defaultValue = "desc") String order) {
        return blocking(() -> AdminEdge.okList(noticeService.querySelective(
                title, content, page, limit, AdminEdge.sort(sort, "add_time"), AdminEdge.order(order))));
    }

    @GetMapping("/read")
    public Mono<ApiResponse<?>> read(@RequestParam Integer id) {
        return blocking(() -> {
            Map<String, Object> data = new HashMap<>(2);
            data.put("notice", noticeService.findById(id));
            data.put("noticeAdminList", noticeAdminService.queryByNoticeId(id));
            return ApiResponse.ok(data);
        });
    }

    @PostMapping("/create")
    public Mono<ApiResponse<?>> create(@RequestBody LitemallNotice notice, Authentication authentication) {
        return blocking(() -> {
            if (notice.getTitle() == null || notice.getTitle().isEmpty()) {
                return AdminEdge.badArgument();
            }
            notice.setAdminId(AdminEdge.adminId(authentication));
            noticeService.add(notice);
            LitemallNoticeAdmin noticeAdmin = new LitemallNoticeAdmin();
            noticeAdmin.setNoticeId(notice.getId());
            noticeAdmin.setNoticeTitle(notice.getTitle());
            for (LitemallAdmin admin : adminService.all()) {
                noticeAdmin.setAdminId(admin.getId());
                noticeAdminService.add(noticeAdmin);
            }
            return ApiResponse.ok(notice);
        });
    }

    @PostMapping("/update")
    public Mono<ApiResponse<?>> update(@RequestBody LitemallNotice notice, Authentication authentication) {
        return blocking(() -> {
            if (notice.getId() == null || notice.getTitle() == null || notice.getTitle().isEmpty()) {
                return AdminEdge.badArgument();
            }
            LitemallNotice original = noticeService.findById(notice.getId());
            if (original == null) {
                return AdminEdge.badArgument();
            }
            if (noticeAdminService.countReadByNoticeId(notice.getId()) > 0) {
                return ApiResponse.fail(AdminEdge.NOTICE_UPDATE_NOT_ALLOWED,
                        "notice has been read and can no longer be edited");
            }
            notice.setAdminId(AdminEdge.adminId(authentication));
            noticeService.updateById(notice);
            if (!original.getTitle().equals(notice.getTitle())) {
                LitemallNoticeAdmin noticeAdmin = new LitemallNoticeAdmin();
                noticeAdmin.setNoticeTitle(notice.getTitle());
                noticeAdminService.updateByNoticeId(noticeAdmin, notice.getId());
            }
            return ApiResponse.ok(notice);
        });
    }

    @PostMapping("/delete")
    public Mono<ApiResponse<?>> delete(@RequestBody LitemallNotice notice) {
        return blocking(() -> {
            if (notice.getId() == null) {
                return AdminEdge.badArgument();
            }
            noticeAdminService.deleteByNoticeId(notice.getId());
            noticeService.deleteById(notice.getId());
            return ApiResponse.ok(null);
        });
    }
}
