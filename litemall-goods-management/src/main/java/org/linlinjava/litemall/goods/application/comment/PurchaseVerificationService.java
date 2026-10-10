package org.linlinjava.litemall.goods.application.comment;

import org.linlinjava.litemall.db.dao.LitemallOrderGoodsMapper;
import org.linlinjava.litemall.db.dao.LitemallOrderMapper;
import org.linlinjava.litemall.db.domain.LitemallOrder;
import org.linlinjava.litemall.db.domain.LitemallOrderExample;
import org.linlinjava.litemall.db.domain.LitemallOrderGoods;
import org.linlinjava.litemall.db.domain.LitemallOrderGoodsExample;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * "Did this customer receive this product, and is that purchase still unreviewed?" — the
 * purchase link behind {@code POST /srv/comment/post} (order-lifecycle audit F17, 2026-09-13).
 *
 * <p>Reads {@code litemall_order} + {@code litemall_order_goods} READ-ONLY through the shared
 * litemall-db mappers — the {@code CouponSignalResolver} / {@code GrouponSignalResolver}
 * precedent (goods-management reading another context's table, never its HTTP surface). The
 * ONE write this class performs is the reviewed mark on the consumed line
 * ({@code litemall_order_goods.comment}, V1 semantics: 0 = reviewable, &gt;0 = comment id), as a
 * compare-and-set so two posts against the same purchase can never both succeed. That column
 * has had no writer anywhere since the Wave-4 wx-api decommission; the user approved this
 * cross-context write on 2026-09-13 over a fail-soft order-side endpoint.
 *
 * <p>An eligible line is: the order belongs to the caller, is not deleted, and sits at 401
 * (DELIVERED) or 402 (AUTO_DELIVERED); the line carries the goods id, is not deleted, and its
 * {@code comment} is 0 (or NULL, treated as 0). Lines are consumed oldest-first, so a customer
 * who bought the product twice may review it twice — one review per purchase, never more.
 * {@code deleted} is bound as a literal: {@code andLogicalDeleted()} is inverted across the
 * domain classes (see CLAUDE.md) and must not be called.
 */
@Service
public class PurchaseVerificationService {

    /** Order statuses under which a purchase counts as received (LitemallOrderStatus 401/402). */
    static final List<Short> RECEIVED_STATUSES = List.of((short) 401, (short) 402);

    /** Outcome of {@link #findEligibleLine}: exactly one of {@code line} / {@code refusal} is set. */
    public record Eligibility(LitemallOrderGoods line, Refusal refusal) {
        public boolean eligible() {
            return line != null;
        }
        static Eligibility of(LitemallOrderGoods line) {
            return new Eligibility(line, null);
        }
        static Eligibility refused(Refusal refusal) {
            return new Eligibility(null, refusal);
        }
    }

    public enum Refusal {
        /** No delivered order line for this goods (or not for the given order). */
        NOT_PURCHASED,
        /** Delivered line(s) exist but every one already carries a review. */
        ALREADY_REVIEWED
    }

    private final LitemallOrderMapper orderMapper;
    private final LitemallOrderGoodsMapper orderGoodsMapper;

    public PurchaseVerificationService(LitemallOrderMapper orderMapper,
                                       LitemallOrderGoodsMapper orderGoodsMapper) {
        this.orderMapper = orderMapper;
        this.orderGoodsMapper = orderGoodsMapper;
    }

    /**
     * The oldest unreviewed delivered line of {@code goodsId} bought by {@code userId}; when
     * {@code orderId} is given, only that order is considered (an order that is not the
     * caller's, not delivered or does not contain the goods answers NOT_PURCHASED — never a
     * fallback to some other order, so the order-detail path cannot silently consume a
     * different purchase than the one the customer is looking at).
     */
    public Eligibility findEligibleLine(Integer userId, Integer goodsId, Integer orderId) {
        if (userId == null || goodsId == null) {
            return Eligibility.refused(Refusal.NOT_PURCHASED);
        }
        LitemallOrderExample orders = new LitemallOrderExample();
        LitemallOrderExample.Criteria oc = orders.createCriteria()
                .andUserIdEqualTo(userId)
                .andOrderStatusIn(RECEIVED_STATUSES)
                .andDeletedEqualTo(false);
        if (orderId != null) {
            oc.andIdEqualTo(orderId);
        }
        List<Integer> orderIds = orderMapper.selectByExampleSelective(orders, LitemallOrder.Column.id)
                .stream().map(LitemallOrder::getId).filter(id -> id != null).toList();
        if (orderIds.isEmpty()) {
            return Eligibility.refused(Refusal.NOT_PURCHASED);
        }
        LitemallOrderGoodsExample lines = new LitemallOrderGoodsExample();
        lines.createCriteria()
                .andOrderIdIn(orderIds)
                .andGoodsIdEqualTo(goodsId)
                .andDeletedEqualTo(false);
        lines.setOrderByClause("add_time asc, id asc");
        List<LitemallOrderGoods> rows = orderGoodsMapper.selectByExample(lines);
        if (rows.isEmpty()) {
            return Eligibility.refused(Refusal.NOT_PURCHASED);
        }
        return rows.stream()
                .filter(PurchaseVerificationService::unreviewed)
                .findFirst()
                .map(Eligibility::of)
                .orElseGet(() -> Eligibility.refused(Refusal.ALREADY_REVIEWED));
    }

    /**
     * Stamp {@code commentId} on the line — compare-and-set on {@code comment = 0 / NULL}, so
     * the SECOND of two concurrent posts against one purchase sees 0 rows and must roll its
     * review back. Returns whether this call won the line.
     */
    public boolean markReviewed(Integer orderGoodsId, Integer commentId) {
        if (orderGoodsId == null || commentId == null || commentId <= 0) {
            return false;
        }
        LitemallOrderGoods patch = new LitemallOrderGoods();
        patch.setComment(commentId);
        patch.setUpdateTime(LocalDateTime.now());
        LitemallOrderGoodsExample example = new LitemallOrderGoodsExample();
        example.createCriteria().andIdEqualTo(orderGoodsId).andCommentEqualTo(0);
        example.or().andIdEqualTo(orderGoodsId).andCommentIsNull();
        return orderGoodsMapper.updateByExampleSelective(patch, example) == 1;
    }

    static boolean unreviewed(LitemallOrderGoods line) {
        return line.getComment() == null || line.getComment() == 0;
    }
}
