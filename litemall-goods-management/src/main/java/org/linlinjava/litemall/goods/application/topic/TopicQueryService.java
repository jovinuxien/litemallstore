package org.linlinjava.litemall.goods.application.topic;

import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallTopic;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.db.service.LitemallTopicService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anonymous customer read queries for topics / marketing articles (litemall-wx-api
 * {@code WxTopicController} parity). {@code detail} resolves the topic's associated goods from
 * {@code litemall-db}. See {@code BrandQueryService} for the altitude note.
 *
 * <p>{@code userHasCollect} is always 0 here: collection/favorites is the user bounded context
 * (out of this service's scope) and these endpoints are anonymous-first. Wiring the real collect
 * state is a user-service follow-up.
 */
@Service
public class TopicQueryService {

    private final LitemallTopicService topicService;
    private final LitemallGoodsService goodsService;

    public TopicQueryService(LitemallTopicService topicService,
                             LitemallGoodsService goodsService) {
        this.topicService = topicService;
        this.goodsService = goodsService;
    }

    /**
     * Paginated topic list (PageHelper-backed; the same list instance is returned so
     * {@code okList} reports the true total). Each row carries {@code goodsCount}: how many of
     * the topic's curated goods are still live.
     *
     * <p>The count is what makes the row honest on a narrowed catalogue. The list payload omits
     * the {@code goods} column, so a client could previously only tell a substantial topic from
     * an empty one by opening each topic's detail — which the storefront capped at the first
     * handful of topics, leaving a real topic sorted below the cap unable to light its nav entry
     * (see handoff-topic-goods-count.md).
     */
    public List<LitemallTopic> list(Integer page, Integer limit, String sort, String order) {
        List<LitemallTopic> topics = topicService.queryList(page, limit, sort, order);
        topicService.attachGoodsCounts(topics);
        return topics;
    }

    /** Topic + its associated goods (+ userHasCollect=0), or null when the topic is absent. */
    public Map<String, Object> detail(Integer id) {
        LitemallTopic topic = topicService.findById(id);
        if (topic == null) {
            return null;
        }
        List<LitemallGoods> goods = new ArrayList<>();
        if (topic.getGoods() != null) {
            for (Integer gid : topic.getGoods()) {
                LitemallGoods good = goodsService.findByIdVO(gid);
                if (good != null) {
                    goods.add(good);
                }
            }
        }
        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("topic", topic);
        entity.put("goods", goods);
        entity.put("userHasCollect", 0);
        return entity;
    }

    /** Up to 4 related topics. */
    public List<LitemallTopic> related(Integer id) {
        return topicService.queryRelatedList(id, 0, 4);
    }
}
