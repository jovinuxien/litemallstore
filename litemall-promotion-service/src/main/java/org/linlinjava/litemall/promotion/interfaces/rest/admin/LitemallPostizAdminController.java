package org.linlinjava.litemall.promotion.interfaces.rest.admin;

import org.linlinjava.litemall.db.domain.LitemallPostizPost;
import org.linlinjava.litemall.promotion.application.internal.PostizPublishServiceImpl;
import org.linlinjava.litemall.promotion.application.internal.PostizPublishServiceImpl.BatchPreview;
import org.linlinjava.litemall.promotion.application.internal.PostizPublishServiceImpl.BatchRequest;
import org.linlinjava.litemall.promotion.application.internal.PostizPublishServiceImpl.ChannelOutcome;
import org.linlinjava.litemall.promotion.application.internal.PostizPublishServiceImpl.ChannelView;
import org.linlinjava.litemall.promotion.application.internal.PostizPublishServiceImpl.PostizRequestException;
import org.linlinjava.litemall.promotion.application.internal.PostizPublishServiceImpl.ProductResult;
import org.linlinjava.litemall.promotion.application.internal.PostizPublishServiceImpl.Status;
import org.linlinjava.litemall.promotion.application.ports.PostizGatewayException;
import org.linlinjava.litemall.promotion.domain.model.repositories.LitemallPostizPostRepository.PostizPostPage;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.ApiResponse;
import org.linlinjava.litemall.promotion.interfaces.dtos.PostizBatchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Wave-17 admin endpoints for Postiz social publishing. Lives under
 * {@code /srv/private/admin/**} (svcsecurity gates to {@code ROLE_ADMIN};
 * gateway-admin's {@code /srv/private/admin/promotion/**} route already reaches
 * this service — no yml change). Contract in CLAUDE.md Wave-17.
 *
 * <p>Fail-soft: Postiz env unset ⇒ typed errno 760 on every endpoint (the UI's
 * visibility switch), never a 5xx; Postiz validation 400s surface verbatim per
 * channel inside 2xx result rows.
 */
@RestController
@RequestMapping("/srv/private/admin/promotion/postiz")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
public class LitemallPostizAdminController {

    private static final Logger logger = LoggerFactory.getLogger(LitemallPostizAdminController.class);

    /** Explicit ISO strings — this module must never leak LocalDateTime array serialization. */
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final PostizPublishServiceImpl service;

    public LitemallPostizAdminController(PostizPublishServiceImpl service) {
        this.service = service;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        Status status = service.status();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", status.enabled());
        if (status.enabled() && status.channelCount() != null) {
            body.put("channelCount", status.channelCount());
        }
        return ApiResponse.ok(body);
    }

    @GetMapping("/channels")
    public ApiResponse<Map<String, Object>> channels() {
        try {
            List<Map<String, Object>> list = service.channelViews().stream()
                    .map(this::toChannelDto)
                    .collect(Collectors.toList());
            return ApiResponse.ok(Map.of("list", list));
        } catch (PostizRequestException e) {
            return ApiResponse.fail(e.getErrno(), e.getMessage());
        } catch (PostizGatewayException e) {
            return unreachable(e);
        }
    }

    @PostMapping("/preview")
    public ApiResponse<Map<String, Object>> preview(@RequestBody PostizBatchRequest request) {
        try {
            BatchPreview preview = service.preview(toBatchRequest(request));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("batch", preview.batch().stream().map(this::toPreviewDto).collect(Collectors.toList()));
            body.put("warnings", preview.warnings());
            return ApiResponse.ok(body);
        } catch (PostizRequestException e) {
            return ApiResponse.fail(e.getErrno(), e.getMessage());
        } catch (PostizGatewayException e) {
            return unreachable(e);
        }
    }

    @PostMapping("/publish")
    public ApiResponse<Map<String, Object>> publish(
            @RequestBody PostizBatchRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String adminId) {
        try {
            List<ProductResult> results = service.publish(toBatchRequest(request),
                    StringUtils.hasText(adminId) ? adminId : "admin");
            return ApiResponse.ok(Map.of("results",
                    results.stream().map(this::toResultDto).collect(Collectors.toList())));
        } catch (PostizRequestException e) {
            return ApiResponse.fail(e.getErrno(), e.getMessage());
        } catch (PostizGatewayException e) {
            return unreachable(e);
        }
    }

    @GetMapping("/log")
    public ApiResponse<Map<String, Object>> log(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int limit) {
        try {
            PostizPostPage result = service.log(page, limit);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("total", result.total());
            body.put("page", page);
            body.put("limit", limit);
            body.put("list", result.rows().stream().map(this::toLogDto).collect(Collectors.toList()));
            return ApiResponse.ok(body);
        } catch (PostizRequestException e) {
            return ApiResponse.fail(e.getErrno(), e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Mapping
    // ------------------------------------------------------------------

    private BatchRequest toBatchRequest(PostizBatchRequest request) {
        return new BatchRequest(request.getGoodsIds(), request.getChannelIds(),
                request.getStartTime(), request.getIntervalMinutes(), request.getCategoryId());
    }

    private ApiResponse<Map<String, Object>> unreachable(PostizGatewayException e) {
        logger.warn("Postiz gateway failure: {}", e.getMessage());
        return ApiResponse.fail(PostizPublishServiceImpl.ERRNO_POSTIZ_UNREACHABLE,
                "Postiz unreachable: " + e.getMessage());
    }

    private Map<String, Object> toChannelDto(ChannelView view) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("integrationId", view.integrationId());
        dto.put("identifier", view.identifier());
        dto.put("name", view.name());
        dto.put("picture", view.picture());
        dto.put("supported", view.supported());
        if (view.reason() != null) {
            dto.put("reason", view.reason());
        }
        return dto;
    }

    private Map<String, Object> toPreviewDto(PostizPublishServiceImpl.ProductPreview preview) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("goodsId", preview.goodsId());
        dto.put("name", preview.name());
        dto.put("picUrl", preview.picUrl());
        dto.put("scheduleAt", preview.scheduleAt());
        dto.put("warnings", preview.warnings());
        dto.put("perChannel", preview.perChannel().stream().map(channel -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("integrationId", channel.integrationId());
            row.put("content", channel.content());
            row.put("settings", channel.settings());
            return row;
        }).collect(Collectors.toList()));
        return dto;
    }

    private Map<String, Object> toResultDto(ProductResult result) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("goodsId", result.goodsId());
        dto.put("scheduleAt", result.scheduleAt());
        dto.put("channels", result.channels().stream().map(this::toOutcomeDto).collect(Collectors.toList()));
        return dto;
    }

    private Map<String, Object> toOutcomeDto(ChannelOutcome outcome) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("integrationId", outcome.integrationId());
        dto.put("ok", outcome.ok());
        if (outcome.postizPostId() != null) {
            dto.put("postizPostId", outcome.postizPostId());
        }
        if (outcome.error() != null) {
            dto.put("error", outcome.error());
        }
        return dto;
    }

    private Map<String, Object> toLogDto(LitemallPostizPost row) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", row.getId());
        dto.put("goodsId", row.getGoodsId());
        dto.put("categoryId", row.getCategoryId());
        dto.put("integrationId", row.getIntegrationId());
        dto.put("channelIdentifier", row.getChannelIdentifier());
        dto.put("postizPostId", row.getPostizPostId());
        // schedule_time is stored UTC; add_time on the server clock. Explicit strings both.
        dto.put("scheduleTime", row.getScheduleTime() != null
                ? row.getScheduleTime().atOffset(ZoneOffset.UTC).format(ISO) : null);
        dto.put("status", row.getStatus());
        dto.put("error", row.getError());
        dto.put("postedBy", row.getPostedBy());
        dto.put("addTime", row.getAddTime() != null
                ? row.getAddTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
        return dto;
    }
}
