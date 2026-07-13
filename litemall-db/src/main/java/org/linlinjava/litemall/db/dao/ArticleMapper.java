package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.LitemallArticle;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Hand-written mapper for {@code litemall_article} (V36). Mirrors
 * {@link CjSourcingRequestMapper}: co-located with the generated DAOs so the
 * existing {@code @MapperScan} + {@code dao/*.xml} mapper-location pattern
 * picks it up.
 *
 * <p>List selects deliberately OMIT the MEDIUMTEXT {@code content} column;
 * only the by-id reads load it.
 */
public interface ArticleMapper {

    /** Insert; populates the generated id back onto the record. */
    int insert(LitemallArticle record);

    /** Any status (admin read), non-deleted. Loads content. */
    LitemallArticle selectById(@Param("id") Integer id);

    /** Customer read: published + non-deleted only. Loads content. */
    LitemallArticle selectPublishedById(@Param("id") Integer id);

    /**
     * Customer list: published, non-deleted, newest first, WITHOUT content.
     * Nullable filters: categoryId; hotOnly=true restricts to is_hot rows.
     */
    List<LitemallArticle> selectCustomerPage(@Param("categoryId") Integer categoryId,
                                             @Param("hotOnly") boolean hotOnly,
                                             @Param("offset") int offset,
                                             @Param("limit") int limit);

    int countCustomer(@Param("categoryId") Integer categoryId,
                      @Param("hotOnly") boolean hotOnly);

    /**
     * Admin list: ALL statuses, non-deleted, newest first, WITHOUT content.
     * Nullable filters: title (contains), categoryId, status (exact).
     */
    List<LitemallArticle> selectAdminPage(@Param("title") String title,
                                          @Param("categoryId") Integer categoryId,
                                          @Param("status") String status,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);

    int countAdmin(@Param("title") String title,
                   @Param("categoryId") Integer categoryId,
                   @Param("status") String status);

    /** Selective update (null fields untouched); update_time always written. */
    int updateSelective(LitemallArticle record);

    /** Atomic view_count = view_count + 1 on a published, non-deleted row. */
    int incrementViewCount(@Param("id") Integer id);

    int logicalDeleteById(@Param("id") Integer id, @Param("updateTime") LocalDateTime updateTime);

    /** Non-deleted articles referencing a category (guards category delete). */
    int countByCategory(@Param("categoryId") Integer categoryId);
}
