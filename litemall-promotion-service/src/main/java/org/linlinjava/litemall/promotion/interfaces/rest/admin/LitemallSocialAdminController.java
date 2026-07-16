package org.linlinjava.litemall.promotion.interfaces.rest.admin;

import org.linlinjava.litemall.promotion.application.internal.LitemallSocialPostServiceImpl;
import org.linlinjava.litemall.promotion.application.internal.LitemallSocialPostServiceImpl.ComposePreview;
import org.linlinjava.litemall.promotion.application.internal.LitemallSocialPostServiceImpl.PlatformAvailability;
import org.linlinjava.litemall.promotion.application.internal.LitemallSocialPostServiceImpl.PostPage;
import org.linlinjava.litemall.promotion.application.internal.LitemallSocialPostServiceImpl.PublishOutcome;
import org.linlinjava.litemall.promotion.application.internal.LitemallSocialPostServiceImpl.RetryResult;
import org.linlinjava.litemall.promotion.domain.model.aggregates.LitemallSocialPostAggregate;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallSocialPlatform;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.enums.LitemallSocialPostStatus;
import org.linlinjava.litemall.promotion.interfaces.dtos.SocialComposePreviewDtoResponse;
import org.linlinjava.litemall.promotion.interfaces.dtos.SocialPostDtoResponse;
import org.linlinjava.litemall.promotion.interfaces.dtos.SocialPostRequest;
import org.linlinjava.litemall.promotion.interfaces.dtos.SocialPublishResultDtoResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin social-composer endpoints (Wave 6). Lives under
 * {@code /srv/private/admin/**}, which litemall-svcsecurity gates to
 * {@code ROLE_ADMIN} (forwarded behind a valid machine token); gateway-admin
 * routes {@code /srv/private/admin/social/**} here. Contract for the SPA is
 * documented in {@code docs/handoff-social-composer.md}.
 *
 * <p>Publishing is fail-soft end to end: a post to disabled/unconfigured
 * platforms still answers 2xx with honest per-platform {@code failed} results
 * (+ failed ledger rows) — 4xx is reserved for caller mistakes (unknown goods,
 * bad platform value, retrying a non-failed row).
 */
@RestController
@RequestMapping("/srv/private/admin/social")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
public class LitemallSocialAdminController {

    private final LitemallSocialPostServiceImpl socialPostService;

    public LitemallSocialAdminController(LitemallSocialPostServiceImpl socialPostService) {
        this.socialPostService = socialPostService;
    }

    @GetMapping("/compose-preview")
    public ResponseEntity<?> composePreview(@RequestParam Integer goodsId) {
        return socialPostService.composePreview(goodsId)
                .<ResponseEntity<?>>map(preview -> ResponseEntity.ok(toPreviewDto(preview)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/post")
    public ResponseEntity<?> post(@RequestBody SocialPostRequest request,
                                  @RequestHeader(value = "X-User-Id", required = false) String adminId) {
        if (request.getGoodsId() == null) {
            return badRequest("goodsId is required");
        }
        if (request.getPlatforms() == null || request.getPlatforms().isEmpty()) {
            return badRequest("platforms is required (meta_fb | meta_ig | tiktok)");
        }
        List<LitemallSocialPlatform> platforms = new ArrayList<>();
        for (String value : request.getPlatforms()) {
            LitemallSocialPlatform platform = LitemallSocialPlatform.fromDbValue(value);
            if (platform == null) {
                return badRequest("unknown platform '" + value + "' (meta_fb | meta_ig | tiktok)");
            }
            platforms.add(platform);
        }
        return socialPostService.post(request.getGoodsId(), request.getCaption(),
                        request.getMediaUrl(), platforms,
                        StringUtils.hasText(adminId) ? adminId : "admin")
                .<ResponseEntity<?>>map(outcomes -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("results", outcomes.stream()
                            .map(this::toResultDto)
                            .collect(Collectors.toList()));
                    return ResponseEntity.ok(body);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String platform) {
        PostPage result = socialPostService.list(page, limit,
                LitemallSocialPostStatus.fromDbValue(status),
                LitemallSocialPlatform.fromDbValue(platform));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", result.total());
        body.put("page", page);
        body.put("limit", limit);
        body.put("list", result.rows().stream().map(this::toRowDto).collect(Collectors.toList()));
        return ResponseEntity.ok(body);
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<?> retry(@PathVariable Integer id) {
        RetryResult result = socialPostService.retry(id);
        if (!result.found()) {
            return ResponseEntity.notFound().build();
        }
        if (!result.retried()) {
            return badRequest("only failed rows can be retried");
        }
        return ResponseEntity.ok(toResultDto(result.outcome()));
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        return ResponseEntity.badRequest().body(body);
    }

    private SocialComposePreviewDtoResponse toPreviewDto(ComposePreview preview) {
        List<SocialComposePreviewDtoResponse.PlatformDto> platforms = new ArrayList<>();
        for (PlatformAvailability availability : preview.platforms()) {
            platforms.add(SocialComposePreviewDtoResponse.PlatformDto.builder()
                    .platform(availability.platform().getDbValue())
                    .displayName(availability.platform().getDisplayName())
                    .enabled(availability.enabled())
                    .available(availability.available())
                    .reason(availability.reason())
                    .shareUrl(availability.shareUrl())
                    .build());
        }
        return SocialComposePreviewDtoResponse.builder()
                .goodsId(preview.goodsId())
                .goodsName(preview.goodsName())
                .price(preview.price())
                .dealPrice(preview.dealPrice())
                .dealId(preview.dealId())
                .caption(preview.caption())
                .images(preview.images())
                .videoUrl(preview.videoUrl())
                .platforms(platforms)
                .build();
    }

    private SocialPublishResultDtoResponse toResultDto(PublishOutcome outcome) {
        return SocialPublishResultDtoResponse.builder()
                .platform(outcome.platform().getDbValue())
                .postId(outcome.postId())
                .status(outcome.status().getDbValue())
                .externalPostId(outcome.externalPostId())
                .error(outcome.error())
                .build();
    }

    private SocialPostDtoResponse toRowDto(LitemallSocialPostAggregate row) {
        return SocialPostDtoResponse.builder()
                .id(row.getId())
                .goodsId(row.getGoodsId())
                .platform(row.getPlatform() != null ? row.getPlatform().getDbValue() : null)
                .caption(row.getCaption())
                .mediaUrl(row.getMediaUrl())
                .linkUrl(row.getLinkUrl())
                .status(row.getStatus() != null ? row.getStatus().getDbValue() : null)
                .externalPostId(row.getExternalPostId())
                .error(row.getError())
                .postedBy(row.getPostedBy())
                .dealId(row.getDealId())
                .addTime(row.getAddTime())
                .updateTime(row.getUpdateTime())
                .build();
    }
}
