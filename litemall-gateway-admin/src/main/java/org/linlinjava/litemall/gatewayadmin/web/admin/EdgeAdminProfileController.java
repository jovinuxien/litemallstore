package org.linlinjava.litemall.gatewayadmin.web.admin;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.linlinjava.litemall.db.domain.LitemallAdmin;
import org.linlinjava.litemall.db.domain.LitemallNotice;
import org.linlinjava.litemall.db.domain.LitemallNoticeAdmin;
import org.linlinjava.litemall.db.service.LitemallAdminService;
import org.linlinjava.litemall.db.service.LitemallNoticeAdminService;
import org.linlinjava.litemall.db.service.LitemallNoticeService;
import org.linlinjava.litemall.gatewayadmin.web.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

import static org.linlinjava.litemall.gatewayadmin.web.admin.AdminEdge.blocking;

/**
 * The signed-in admin's own profile: password change + personal notice inbox.
 * Ported from legacy AdminProfileController — with the crucial difference that
 * legacy's identity was stubbed to {@code adminId=0} after the Shiro removal
 * (every admin shared one inbox and the password check was commented out).
 * Here every operation is scoped to {@code AdminEdge.adminId(authentication)},
 * the id from the verified admin JWT. See {@link AdminEdge}.
 *
 * <p>Notice AUTHORING (create/broadcast) stays in
 * {@link EdgeAdminNoticeController}; this controller is strictly the
 * per-admin read-state side (unread count, list, read, delete).
 */
@RestController
@RequestMapping("/srv/private/admin/profile")
public class EdgeAdminProfileController {

    private final LitemallAdminService adminService;
    private final LitemallNoticeService noticeService;
    private final LitemallNoticeAdminService noticeAdminService;
    /** Same encoder family as AdminCredentialsService — hashes stay interchangeable. */
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public EdgeAdminProfileController(LitemallAdminService adminService,
                                      LitemallNoticeService noticeService,
                                      LitemallNoticeAdminService noticeAdminService) {
        this.adminService = adminService;
        this.noticeService = noticeService;
        this.noticeAdminService = noticeAdminService;
    }

    /** Change own password: {oldPassword, newPassword}. Wrong old password → 605. */
    @PostMapping("/password")
    public Mono<ApiResponse<?>> password(@RequestBody Map<String, String> body,
                                         Authentication authentication) {
        return blocking(() -> {
            String oldPassword = body.get("oldPassword");
            String newPassword = body.get("newPassword");
            if (oldPassword == null || oldPassword.isEmpty()
                    || newPassword == null || newPassword.isEmpty()) {
                return AdminEdge.badArgument();
            }
            if (newPassword.length() < 6) {
                return ApiResponse.fail(AdminEdge.ADMIN_INVALID_PASSWORD,
                        "new password must be at least 6 characters");
            }
            Integer adminId = AdminEdge.adminId(authentication);
            // NB: findById projects {id,username,avatar,roleIds} — password is
            // NULL there and BCrypt.matches would always fail. findAdmin(id)
            // selects the full row.
            LitemallAdmin admin = adminId == null ? null : adminService.findAdmin(adminId);
            if (admin == null || Boolean.TRUE.equals(admin.getDeleted())) {
                return AdminEdge.badArgument();
            }
            if (!encoder.matches(oldPassword, admin.getPassword())) {
                return ApiResponse.fail(AdminEdge.ADMIN_INVALID_ACCOUNT,
                        "old password is incorrect");
            }
            LitemallAdmin update = new LitemallAdmin();
            update.setId(admin.getId());
            update.setPassword(encoder.encode(newPassword));
            if (adminService.updateById(update) == 0) {
                return AdminEdge.updateFailed();
            }
            return ApiResponse.ok(null);
        });
    }

    /** Unread-notice count for the header bell. */
    @GetMapping("/nnotice")
    public Mono<ApiResponse<?>> nnotice(Authentication authentication) {
        return blocking(() -> {
            Integer adminId = AdminEdge.adminId(authentication);
            if (adminId == null) {
                return AdminEdge.badArgument();
            }
            return ApiResponse.ok(noticeAdminService.countUnread(adminId));
        });
    }

    /** Personal inbox list. type: all|read|unread (empty = all). */
    @GetMapping("/lsnotice")
    public Mono<ApiResponse<?>> lsnotice(@RequestParam(required = false) String title,
                                         @RequestParam(defaultValue = "") String type,
                                         @RequestParam(defaultValue = "1") Integer page,
                                         @RequestParam(defaultValue = "10") Integer limit,
                                         @RequestParam(defaultValue = "add_time") String sort,
                                         @RequestParam(defaultValue = "desc") String order,
                                         Authentication authentication) {
        return blocking(() -> {
            Integer adminId = AdminEdge.adminId(authentication);
            if (adminId == null) {
                return AdminEdge.badArgument();
            }
            // NB: querySelective dereferences `type` — never pass null.
            List<LitemallNoticeAdmin> list = noticeAdminService.querySelective(
                    title, type == null ? "" : type, adminId, page, limit,
                    AdminEdge.sort(sort, "add_time"), AdminEdge.order(order));
            return AdminEdge.okList(list);
        });
    }

    /** Open one notice: marks it read and returns its content. Body: {noticeId}. */
    @PostMapping("/catnotice")
    public Mono<ApiResponse<?>> catnotice(@RequestBody Map<String, Integer> body,
                                          Authentication authentication) {
        return blocking(() -> {
            Integer noticeId = body.get("noticeId");
            if (noticeId == null) {
                return AdminEdge.badArgument();
            }
            Integer adminId = AdminEdge.adminId(authentication);
            if (adminId == null) {
                return AdminEdge.badArgument();
            }
            LitemallNoticeAdmin noticeAdmin = noticeAdminService.find(noticeId, adminId);
            if (noticeAdmin == null) {
                return AdminEdge.badArgumentValue();
            }
            if (noticeAdmin.getReadTime() == null) {
                noticeAdmin.setReadTime(LocalDateTime.now());
                noticeAdminService.update(noticeAdmin);
            }
            LitemallNotice notice = noticeService.findById(noticeId);
            if (notice == null) {
                return AdminEdge.badArgumentValue();
            }
            Map<String, Object> data = new HashMap<>(5);
            data.put("title", notice.getTitle());
            data.put("content", notice.getContent());
            data.put("time", notice.getUpdateTime());
            Integer authorId = notice.getAdminId();
            if (authorId == null || authorId == 0) {
                data.put("admin", "system");
            } else {
                LitemallAdmin author = adminService.findById(authorId);
                data.put("admin", author != null ? author.getUsername() : "system");
                data.put("avatar", author != null ? author.getAvatar() : null);
            }
            return ApiResponse.ok(data);
        });
    }

    /** Batch mark-read. Body: {ids: [...]}. */
    @PostMapping("/bcatnotice")
    public Mono<ApiResponse<?>> bcatnotice(@RequestBody Map<String, List<Integer>> body,
                                           Authentication authentication) {
        return blocking(() -> {
            List<Integer> ids = body.get("ids");
            Integer adminId = AdminEdge.adminId(authentication);
            if (ids == null || ids.isEmpty() || adminId == null) {
                return AdminEdge.badArgument();
            }
            noticeAdminService.markReadByIds(ids, adminId);
            return ApiResponse.ok(null);
        });
    }

    /** Delete one inbox row (soft). Body: {id} — the litemall_notice_admin row id. */
    @PostMapping("/rmnotice")
    public Mono<ApiResponse<?>> rmnotice(@RequestBody Map<String, Integer> body,
                                         Authentication authentication) {
        return blocking(() -> {
            Integer id = body.get("id");
            Integer adminId = AdminEdge.adminId(authentication);
            if (id == null || adminId == null) {
                return AdminEdge.badArgument();
            }
            noticeAdminService.deleteById(id, adminId);
            return ApiResponse.ok(null);
        });
    }

    /** Batch delete inbox rows (soft). Body: {ids: [...]}. */
    @PostMapping("/brmnotice")
    public Mono<ApiResponse<?>> brmnotice(@RequestBody Map<String, List<Integer>> body,
                                          Authentication authentication) {
        return blocking(() -> {
            List<Integer> ids = body.get("ids");
            Integer adminId = AdminEdge.adminId(authentication);
            if (ids == null || ids.isEmpty() || adminId == null) {
                return AdminEdge.badArgument();
            }
            noticeAdminService.deleteByIds(ids, adminId);
            return ApiResponse.ok(null);
        });
    }
}
