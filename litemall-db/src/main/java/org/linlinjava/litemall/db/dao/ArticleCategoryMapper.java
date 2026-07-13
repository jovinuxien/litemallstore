package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.LitemallArticleCategory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Hand-written mapper for {@code litemall_article_category} (V36). Mirrors
 * {@link CjSourcingRequestMapper}: co-located with the generated DAOs so the
 * existing {@code @MapperScan} + {@code dao/*.xml} mapper-location pattern
 * picks it up.
 */
public interface ArticleCategoryMapper {

    /** Insert; populates the generated id back onto the record. */
    int insert(LitemallArticleCategory record);

    LitemallArticleCategory selectById(@Param("id") Integer id);

    /** All non-deleted categories, by sort_order then id. */
    List<LitemallArticleCategory> selectAll();

    /** Selective update (null fields untouched); update_time always written. */
    int updateSelective(LitemallArticleCategory record);

    int logicalDeleteById(@Param("id") Integer id, @Param("updateTime") LocalDateTime updateTime);
}
