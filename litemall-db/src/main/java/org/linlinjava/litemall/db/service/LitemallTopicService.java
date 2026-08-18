package org.linlinjava.litemall.db.service;

import com.github.pagehelper.PageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.linlinjava.litemall.db.dao.LitemallCjLinkageMapper;
import org.linlinjava.litemall.db.dao.LitemallTopicMapper;
import org.linlinjava.litemall.db.domain.LitemallTopic;
import org.linlinjava.litemall.db.domain.LitemallTopicExample;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import jakarta.annotation.Resource;


import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class LitemallTopicService {

    private static final Logger logger = LoggerFactory.getLogger(LitemallTopicService.class);

    @Resource
    private LitemallTopicMapper topicMapper;
    @Resource
    private LitemallCjLinkageMapper linkageMapper;
    private LitemallTopic.Column[] columns = new LitemallTopic.Column[]{LitemallTopic.Column.id, LitemallTopic.Column.title, LitemallTopic.Column.subtitle, LitemallTopic.Column.price, LitemallTopic.Column.picUrl, LitemallTopic.Column.readCount};

    public List<LitemallTopic> queryList(int offset, int limit) {
        return queryList(offset, limit, "add_time", "desc");
    }

    public List<LitemallTopic> queryList(int offset, int limit, String sort, String order) {
        LitemallTopicExample example = new LitemallTopicExample();
        example.or().andDeletedEqualTo(false);
        example.setOrderByClause(sort + " " + order);
        PageHelper.startPage(offset, limit);
        return topicMapper.selectByExampleSelective(example, columns);
    }

    public int queryTotal() {
        LitemallTopicExample example = new LitemallTopicExample();
        example.or().andDeletedEqualTo(false);
        return (int) topicMapper.countByExample(example);
    }

    public LitemallTopic findById(Integer id) {
        LitemallTopicExample example = new LitemallTopicExample();
        example.or().andIdEqualTo(id).andDeletedEqualTo(false);
        return topicMapper.selectOneByExampleWithBLOBs(example);
    }

    public List<LitemallTopic> queryRelatedList(Integer id, int offset, int limit) {
        LitemallTopicExample example = new LitemallTopicExample();
        example.or().andIdEqualTo(id).andDeletedEqualTo(false);
        List<LitemallTopic> topics = topicMapper.selectByExample(example);
        if (topics.size() == 0) {
            return queryList(offset, limit, "add_time", "desc");
        }
        LitemallTopic topic = topics.get(0);

        example = new LitemallTopicExample();
        example.or().andIdNotEqualTo(topic.getId()).andDeletedEqualTo(false);
        PageHelper.startPage(offset, limit);
        List<LitemallTopic> relateds = topicMapper.selectByExampleWithBLOBs(example);
        if (relateds.size() != 0) {
            return relateds;
        }

        return queryList(offset, limit, "add_time", "desc");
    }

    public List<LitemallTopic> querySelective(String title, String subtitle, Integer page, Integer limit, String sort, String order) {
        LitemallTopicExample example = new LitemallTopicExample();
        LitemallTopicExample.Criteria criteria = example.createCriteria();

        if (!StringUtils.isEmpty(title)) {
            criteria.andTitleLike("%" + title + "%");
        }
        if (!StringUtils.isEmpty(subtitle)) {
            criteria.andSubtitleLike("%" + subtitle + "%");
        }
        criteria.andDeletedEqualTo(false);

        if (!StringUtils.isEmpty(sort) && !StringUtils.isEmpty(order)) {
            example.setOrderByClause(sort + " " + order);
        }

        PageHelper.startPage(page, limit);
        return topicMapper.selectByExampleWithBLOBs(example);
    }

    public int updateById(LitemallTopic topic) {
        topic.setUpdateTime(LocalDateTime.now());
        LitemallTopicExample example = new LitemallTopicExample();
        example.or().andIdEqualTo(topic.getId());
        return topicMapper.updateByExampleSelective(topic, example);
    }

    public void deleteById(Integer id) {
        topicMapper.logicalDeleteByPrimaryKey(id);
    }

    public void add(LitemallTopic topic) {
        topic.setAddTime(LocalDateTime.now());
        topic.setUpdateTime(LocalDateTime.now());
        topicMapper.insertSelective(topic);
    }


    /**
     * Attach the computed {@code goodsCount} to each row, in place — the PageHelper page object
     * is preserved, so {@code ResponseUtil.okList} still reports the true total (rebuilding the
     * list into plain maps would silently collapse {@code total} to the page size).
     *
     * <p>Two queries regardless of page size. The list query deliberately omits the {@code goods}
     * column, so the curated id arrays are re-read for the listed topics, then the live subset of
     * every referenced id is resolved in one batch. Ids are counted per occurrence rather than
     * deduplicated, because {@code /srv/topic/detail} renders one tile per entry — the count and
     * the page it describes stay identical even for a malformed topic that lists an id twice.
     *
     * <p>Topics with no goods, or whose goods have all been off-saled (the Wave-26 narrowing did
     * exactly that to the seed topics), get {@code 0} — never null. Null means "nobody attached a
     * count", and callers rely on that distinction: it is also what an unreadable {@code goods}
     * column degrades to, rather than failing the list.
     */
    public void attachGoodsCounts(List<LitemallTopic> topics) {
        if (topics == null || topics.isEmpty()) {
            return;
        }
        List<Integer> ids = topics.stream().map(LitemallTopic::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return;
        }

        LitemallTopicExample example = new LitemallTopicExample();
        example.or().andIdIn(ids).andDeletedEqualTo(false);
        Map<Integer, Integer[]> curated = new HashMap<>();
        try {
            for (LitemallTopic row : topicMapper.selectByExampleSelective(example,
                    new LitemallTopic.Column[]{LitemallTopic.Column.id, LitemallTopic.Column.goods})) {
                curated.put(row.getId(), row.getGoods());
            }
        } catch (RuntimeException e) {
            // JsonIntegerArrayTypeHandler throws on a malformed goods column, and it fails the
            // whole result set, not one row. Before counts existed the list never read that
            // column, so a corrupt row could not affect it — degrade back to that behaviour
            // (counts stay null = "nobody attached a count") instead of turning one bad row into
            // a 500 on an anonymous, cacheable endpoint.
            logger.warn("topic goods counts unavailable: unreadable goods column among topics {}", ids, e);
            return;
        }

        Set<Integer> referenced = new LinkedHashSet<>();
        for (Integer[] goods : curated.values()) {
            if (goods == null) {
                continue;
            }
            for (Integer goodsId : goods) {
                if (goodsId != null) {
                    referenced.add(goodsId);
                }
            }
        }
        Set<Integer> live = referenced.isEmpty()
                ? Set.of()
                : new HashSet<>(linkageMapper.selectOnSaleGoodsIds(new ArrayList<>(referenced)));

        for (LitemallTopic topic : topics) {
            Integer[] goods = curated.get(topic.getId());
            int count = 0;
            if (goods != null) {
                for (Integer goodsId : goods) {
                    if (goodsId != null && live.contains(goodsId)) {
                        count++;
                    }
                }
            }
            topic.setGoodsCount(count);
        }
    }

    public void deleteByIds(List<Integer> ids) {
        LitemallTopicExample example = new LitemallTopicExample();
        example.or().andIdIn(ids).andDeletedEqualTo(false);
        LitemallTopic topic = new LitemallTopic();
        topic.setUpdateTime(LocalDateTime.now());
        topic.setDeleted(true);
        topicMapper.updateByExampleSelective(topic, example);
    }
}
