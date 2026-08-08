package org.linlinjava.litemall.order.infrastructure.services.acl.facades;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.Util;
import org.linlinjava.litemall.order.application.util.exception.coupon.LitemallInvalidCouponException;
import org.linlinjava.litemall.order.application.util.exception.coupon.LitemallPromotionServiceUnavailableException;
import org.linlinjava.litemall.order.domain.model.valueobjects.order.LitemallOrderId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.promotion.CouponRedemption;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.promotion.GroupBuyCampaign;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.promotion.GroupBuySlot;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.promotion.UsableCoupon;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.PromotionServiceFeignClient;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.utils.CouponRedeemRequest;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.utils.CouponReleaseRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adapter for the promotion coupon contract. Maps promotion's raw JSON onto the
 * small VOs the placement path needs and translates outcomes into the order side's
 * typed exceptions: business 400s on redeem become
 * {@link LitemallInvalidCouponException} (client error, placement aborts, no order),
 * transport-level failures become
 * {@link LitemallPromotionServiceUnavailableException} (503, retryable). Release is
 * compensation and therefore never throws — promotion's release is idempotent and
 * replay-safe, so retries are the caller's prerogative, not an obligation.
 */
@Component
public class LitemallPromotionFacadeImpl implements LitemallPromotionFacade {

    private static final Logger log = LoggerFactory.getLogger(LitemallPromotionFacadeImpl.class);

    /** Promotion's wire code for a USED holding on {@code GET /my?status=} (numeric, not the enum name). */
    private static final String USED_STATUS_CODE = "1";

    private final PromotionServiceFeignClient promotionClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LitemallPromotionFacadeImpl(PromotionServiceFeignClient promotionClient) {
        this.promotionClient = promotionClient;
    }

    @Override
    public Optional<UsableCoupon> findUsableCoupon(LitemallUserId userId, Integer userCouponId,
                                                   BigDecimal amount, Set<Integer> goodsIds,
                                                   Set<Integer> categoryIds) {
        JsonNode list;
        try {
            list = promotionClient.usableCoupons(userId.getId(), amount, csv(goodsIds), csv(categoryIds));
        } catch (LitemallPromotionServiceUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new LitemallPromotionServiceUnavailableException("usable-coupon lookup", e);
        }
        if (list == null || !list.isArray()) {
            return Optional.empty();
        }
        for (JsonNode c : list) {
            if (c.path("userCouponId").asInt() == userCouponId) {
                return Optional.of(new UsableCoupon(
                        c.path("userCouponId").asInt(),
                        c.path("couponId").asInt(),
                        c.path("name").asText(null),
                        decimalOrNull(c.path("discount")),
                        decimalOrNull(c.path("min"))));
            }
        }
        return Optional.empty();
    }

    @Override
    public CouponRedemption redeemCoupon(LitemallUserId userId, Integer userCouponId,
                                         LitemallOrderId orderId, BigDecimal orderSubtotal,
                                         Set<Integer> goodsIds, Set<Integer> categoryIds) {
        try (Response response = promotionClient.redeemCoupon(userId.getId(), userCouponId,
                new CouponRedeemRequest(orderId.getId(), orderSubtotal, goodsIds, categoryIds))) {
            JsonNode envelope = readEnvelope(response, "redeem of user coupon " + userCouponId);
            if (response.status() == 200 && envelope.path("success").asBoolean(false)) {
                JsonNode data = envelope.path("data");
                return new CouponRedemption(
                        data.path("userCouponId").asInt(userCouponId),
                        data.path("couponId").asInt(),
                        data.path("orderId").asInt(orderId.getId()),
                        decimalOrNull(data.path("discount")));
            }
            if (response.status() == 400) {
                // Business rejection: not usable / expired / not owned / threshold —
                // a clean client error, the coupon was NOT consumed.
                throw new LitemallInvalidCouponException(
                        envelope.path("message").asText("coupon could not be redeemed"));
            }
            throw new LitemallPromotionServiceUnavailableException(
                    "redeem of user coupon " + userCouponId + " (unexpected HTTP " + response.status() + ")", null);
        }
    }

    @Override
    public boolean releaseCoupon(LitemallUserId userId, Integer userCouponId, LitemallOrderId orderId) {
        try (Response response = promotionClient.releaseCoupon(userId.getId(), userCouponId,
                new CouponReleaseRequest(orderId.getId()))) {
            JsonNode envelope = readEnvelope(response, "release of user coupon " + userCouponId);
            if (response.status() == 200 && envelope.path("success").asBoolean(false)) {
                if (envelope.path("data").path("alreadyReleased").asBoolean(false)) {
                    log.info("User coupon {} for order {} was already released (idempotent no-op)",
                            userCouponId, orderId.getId());
                }
                return true;
            }
            // A 400 "not redeemed by this order" means the customer legitimately
            // re-spent the coupon on another order — nothing to compensate.
            log.warn("Release of user coupon {} for order {} refused (HTTP {}): {}",
                    userCouponId, orderId.getId(), response.status(),
                    envelope.path("message").asText(""));
            return false;
        } catch (RuntimeException e) {
            log.error("Release of user coupon {} for order {} failed; promotion's release is "
                    + "idempotent — safe to replay manually if the coupon stays USED",
                    userCouponId, orderId.getId(), e);
            return false;
        }
    }

    @Override
    public Optional<Integer> findRedeemedUserCouponForOrder(LitemallUserId userId, LitemallOrderId orderId) {
        try {
            // Promotion's status filter takes the NUMERIC code (1 = USED); a name like
            // "USED" comes back as a 200 {errno:402} envelope, not an array.
            JsonNode list = promotionClient.myCoupons(userId.getId(), USED_STATUS_CODE);
            if (list != null && list.isArray()) {
                for (JsonNode c : list) {
                    if (c.path("orderId").asInt() == orderId.getId()) {
                        return Optional.of(c.path("userCouponId").asInt());
                    }
                }
            }
        } catch (RuntimeException e) {
            log.warn("Could not look up the redeemed coupon for order {} (best-effort)",
                    orderId.getId(), e);
        }
        return Optional.empty();
    }

    // ---- combination group-buy (Wave 21) --------------------------------------

    @Override
    public Optional<GroupBuySlot> findGroupSlot(LitemallUserId userId, Integer pinkId) {
        try (Response response = promotionClient.pinkDetail(userId.getId(), pinkId)) {
            if (response.status() == 404) {
                return Optional.empty(); // unknown pink — typed stale-slot reject upstream
            }
            if (response.status() != 200) {
                throw new LitemallPromotionServiceUnavailableException(
                        "group slot lookup for pink " + pinkId + " (unexpected HTTP "
                                + response.status() + ")", null);
            }
            JsonNode root = readEnvelope(response, "group slot lookup for pink " + pinkId);
            // GET /pink/{pinkId} answers with the group's LEADER dto (+ members[]);
            // the queried slot is either the top-level node or one of the members.
            JsonNode slot = null;
            if (root.path("pinkId").asInt(-1) == pinkId) {
                slot = root;
            } else {
                for (JsonNode member : root.path("members")) {
                    if (member.path("pinkId").asInt(-1) == pinkId) {
                        slot = member;
                        break;
                    }
                }
            }
            if (slot == null) {
                return Optional.empty();
            }
            return Optional.of(new GroupBuySlot(
                    pinkId,
                    intOrNull(slot.path("combinationId")),
                    intOrNull(slot.path("userId")),
                    intOrNull(slot.path("orderId")),
                    slot.path("status").asText(null)));
        } catch (LitemallPromotionServiceUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new LitemallPromotionServiceUnavailableException(
                    "group slot lookup for pink " + pinkId, e);
        }
    }

    @Override
    public Optional<GroupBuyCampaign> findCombination(Integer combinationId) {
        try (Response response = promotionClient.combinationDetail(combinationId)) {
            if (response.status() == 404) {
                return Optional.empty();
            }
            if (response.status() != 200) {
                throw new LitemallPromotionServiceUnavailableException(
                        "combination lookup " + combinationId + " (unexpected HTTP "
                                + response.status() + ")", null);
            }
            JsonNode c = readEnvelope(response, "combination lookup " + combinationId);
            return Optional.of(new GroupBuyCampaign(
                    intOrNull(c.path("combinationId")),
                    intOrNull(c.path("goodsId")),
                    decimalOrNull(c.path("combinationPrice")),
                    // Tolerant read: promotion's public DTO may not expose the cap yet.
                    intOrNull(c.path("limitPerUser"))));
        } catch (LitemallPromotionServiceUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new LitemallPromotionServiceUnavailableException(
                    "combination lookup " + combinationId, e);
        }
    }

    @Override
    public boolean attachOrderToPink(LitemallUserId userId, Integer pinkId, LitemallOrderId orderId) {
        try (Response response = promotionClient.attachOrderToPink(userId.getId(), pinkId,
                new org.linlinjava.litemall.order.infrastructure.services.feignclients.utils.PinkOrderRequest(orderId.getId()))) {
            if (response.status() == 200) {
                return true;
            }
            // 404 = promotion hasn't shipped the endpoint yet (Wave-21 contract:
            // tolerate while dev catches up); anything else is a refused/failed
            // linkage. Both are logged for replay — the placed order stands either way.
            log.warn("attach-order of order {} to pink {} not confirmed (HTTP {}) — "
                            + "promotion-side linkage missing; replay manually if needed",
                    orderId.getId(), pinkId, response.status());
            return false;
        } catch (RuntimeException e) {
            log.warn("attach-order of order {} to pink {} failed (fail-soft): {}",
                    orderId.getId(), pinkId, e.toString());
            return false;
        }
    }

    @Override
    public boolean releasePinkSlot(LitemallUserId userId, Integer pinkId, LitemallOrderId orderId) {
        try (Response response = promotionClient.releasePinkSlot(userId.getId(), pinkId,
                new org.linlinjava.litemall.order.infrastructure.services.feignclients.utils.PinkOrderRequest(orderId.getId()))) {
            if (response.status() == 200) {
                return true;
            }
            log.warn("release of pink {} for cancelled order {} not confirmed (HTTP {}) — "
                            + "promotion's release is idempotent; replay manually if the slot stays taken",
                    pinkId, orderId.getId(), response.status());
            return false;
        } catch (RuntimeException e) {
            log.warn("release of pink {} for cancelled order {} failed (fail-soft): {}",
                    pinkId, orderId.getId(), e.toString());
            return false;
        }
    }

    // ---- helpers --------------------------------------------------------------

    /** Read a mutation Response body as the promotion operation envelope (never null). */
    private JsonNode readEnvelope(Response response, String operation) {
        try {
            if (response.body() == null) {
                return objectMapper.createObjectNode();
            }
            String body = Util.toString(response.body().asReader(StandardCharsets.UTF_8));
            if (body.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(body);
        } catch (IOException e) {
            throw new LitemallPromotionServiceUnavailableException(operation + " (unreadable response)", e);
        }
    }

    private static String csv(Set<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static BigDecimal decimalOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.decimalValue();
    }

    private static Integer intOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asInt();
    }
}
