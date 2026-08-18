package org.linlinjava.litemall.db;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.dao.LitemallCjLinkageMapper;
import org.linlinjava.litemall.db.dao.LitemallTopicMapper;
import org.linlinjava.litemall.db.domain.LitemallTopic;
import org.linlinjava.litemall.db.domain.LitemallTopicExample;
import org.linlinjava.litemall.db.service.LitemallTopicService;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one new failure mode counts introduce, and how it degrades.
 *
 * <p>{@code JsonIntegerArrayTypeHandler} throws on a malformed {@code goods} column, and it fails
 * the whole result set rather than one row. Before counts existed, {@code /srv/topic/list} never
 * read that column, so a corrupt row could not affect it. Attaching counts must not convert one
 * bad row into a 500 on an anonymous endpoint: the count simply goes unmeasured (null), which is
 * the same thing every caller already sees when talking to a backend that predates the field.
 *
 * <p>No Docker needed — the failure is raised at the mapper seam.
 */
public class TopicGoodsCountDegradeTest {

    /** Minimal stand-in: only the one method attachGoodsCounts calls needs behaviour. */
    private static class ThrowingTopicMapper implements java.lang.reflect.InvocationHandler {
        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            if ("selectByExampleSelective".equals(method.getName())) {
                throw new RuntimeException(
                        "com.fasterxml.jackson.core.JsonParseException: Unexpected character ('x')");
            }
            throw new UnsupportedOperationException(method.getName());
        }
    }

    private static class ExplodingLinkageMapper implements java.lang.reflect.InvocationHandler {
        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            // Reaching the linkage mapper at all means the guard above did not stop the walk.
            throw new AssertionError("must not query goods after an unreadable goods column");
        }
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(java.lang.reflect.Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = LitemallTopicService.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    public void unreadableGoodsColumnLeavesCountsUnmeasuredInsteadOfFailingTheList() throws Exception {
        LitemallTopicService service = new LitemallTopicService();
        set(service, "topicMapper", proxy(LitemallTopicMapper.class, new ThrowingTopicMapper()));
        set(service, "linkageMapper", proxy(LitemallCjLinkageMapper.class, new ExplodingLinkageMapper()));

        LitemallTopic topic = new LitemallTopic();
        topic.setId(7001);
        List<LitemallTopic> topics = new ArrayList<>();
        topics.add(topic);

        service.attachGoodsCounts(topics);

        assertNull(topics.get(0).getGoodsCount(),
                "an unreadable goods column must read as unmeasured, never as a count of 0");
        assertTrue(topics.size() == 1, "the caller's list is left intact");
    }

    /** Guards the example the production code builds, so the filter cannot silently drift. */
    @Test
    public void curatedReadExcludesDeletedTopics() {
        LitemallTopicExample example = new LitemallTopicExample();
        example.or().andIdIn(List.of(1, 2)).andDeletedEqualTo(false);
        assertTrue(example.getOredCriteria().get(0).getAllCriteria().stream()
                        .anyMatch(c -> "deleted =".equals(c.getCondition()) && Boolean.FALSE.equals(c.getValue())),
                "deleted must be bound as a literal false — andLogicalDeleted() is inverted repo-wide");
    }
}
