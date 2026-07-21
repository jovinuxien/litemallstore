package org.linlinjava.litemall.db.service;

import com.github.pagehelper.PageHelper;
import jakarta.annotation.Resource;
import org.linlinjava.litemall.db.dao.LitemallCommentMapper;
import org.linlinjava.litemall.db.domain.LitemallComment;
import org.linlinjava.litemall.db.domain.LitemallCommentExample;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LitemallCommentService {
    @Resource
    private LitemallCommentMapper commentMapper;

    public List<LitemallComment> queryGoodsByGid(Integer id, int offset, int limit) {
        LitemallCommentExample example = new LitemallCommentExample();
        example.setOrderByClause(LitemallComment.Column.addTime.desc());
        example.or().andValueIdEqualTo(id).andTypeEqualTo((byte) 0).andDeletedEqualTo(false);
        PageHelper.startPage(offset, limit);
        return commentMapper.selectByExample(example);
    }

    public List<LitemallComment> query(Byte type, Integer valueId, Integer showType, Integer offset, Integer limit) {
        LitemallCommentExample example = new LitemallCommentExample();
        example.setOrderByClause(LitemallComment.Column.addTime.desc());
        if (showType == 0) {
            example.or().andValueIdEqualTo(valueId).andTypeEqualTo(type).andDeletedEqualTo(false);
        } else if (showType == 1) {
            example.or().andValueIdEqualTo(valueId).andTypeEqualTo(type).andHasPictureEqualTo(true).andDeletedEqualTo(false);
        } else {
            throw new RuntimeException("showType不支持");
        }
        PageHelper.startPage(offset, limit);
        return commentMapper.selectByExample(example);
    }

    public int count(Byte type, Integer valueId, Integer showType) {
        LitemallCommentExample example = new LitemallCommentExample();
        if (showType == 0) {
            example.or().andValueIdEqualTo(valueId).andTypeEqualTo(type).andDeletedEqualTo(false);
        } else if (showType == 1) {
            example.or().andValueIdEqualTo(valueId).andTypeEqualTo(type).andHasPictureEqualTo(true).andDeletedEqualTo(false);
        } else {
            throw new RuntimeException("showType不支持");
        }
        return (int) commentMapper.countByExample(example);
    }

    /**
     * All (value_id, star) pairs for a batch of targets in ONE query — used to aggregate per-goods
     * rating stats for listing pages. Only the two needed columns are fetched.
     */
    public List<LitemallComment> queryByValueIds(Byte type, List<Integer> valueIds) {
        if (valueIds == null || valueIds.isEmpty()) {
            return List.of();
        }
        LitemallCommentExample example = new LitemallCommentExample();
        example.or().andTypeEqualTo(type).andValueIdIn(valueIds).andDeletedEqualTo(false);
        return commentMapper.selectByExampleSelective(example,
                LitemallComment.Column.valueId, LitemallComment.Column.star);
    }

    public int save(LitemallComment comment) {
        comment.setAddTime(LocalDateTime.now());
        comment.setUpdateTime(LocalDateTime.now());
        return commentMapper.insertSelective(comment);
    }

    /**
     * Insert preserving the caller-set add/update times — the CJ review ingest lands external
     * reviews with {@code add_time} = the ORIGINAL CJ comment date, so the merged newest-first
     * ordering interleaves them honestly with customer-posted rows instead of stamping them all
     * "now". Throws {@code DuplicateKeyException} on a (source, external_id) collision, which
     * the ingest treats as already-landed (idempotent re-ingest).
     */
    public int saveRetainingTimes(LitemallComment comment) {
        if (comment.getUpdateTime() == null) {
            comment.setUpdateTime(LocalDateTime.now());
        }
        return commentMapper.insertSelective(comment);
    }

    public List<LitemallComment> querySelective(String userId, String valueId, Integer page, Integer size, String sort, String order) {
        LitemallCommentExample example = new LitemallCommentExample();
        LitemallCommentExample.Criteria criteria = example.createCriteria();

        // type=2 是订单商品回复，这里过滤
        criteria.andTypeNotEqualTo((byte) 2);

        if (!StringUtils.isEmpty(userId)) {
            criteria.andUserIdEqualTo(Integer.valueOf(userId));
        }
        if (!StringUtils.isEmpty(valueId)) {
            criteria.andValueIdEqualTo(Integer.valueOf(valueId)).andTypeEqualTo((byte) 0);
        }
        criteria.andDeletedEqualTo(false);

        if (!StringUtils.isEmpty(sort) && !StringUtils.isEmpty(order)) {
            example.setOrderByClause(sort + " " + order);
        }

        PageHelper.startPage(page, size);
        return commentMapper.selectByExample(example);
    }

    public void deleteById(Integer id) {
        commentMapper.logicalDeleteByPrimaryKey(id);
    }

    public LitemallComment findById(Integer id) {
        return commentMapper.selectByPrimaryKey(id);
    }

    public int updateById(LitemallComment comment) {
        return commentMapper.updateByPrimaryKeySelective(comment);
    }
}
