package org.linlinjava.litemall.goods.application.topic;

import com.github.pagehelper.Page;
import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.db.domain.LitemallTopic;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.db.service.LitemallTopicService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code /srv/topic/list} rows carry a goods count, and the page object survives the trip.
 *
 * <p>The count is what makes the row judgeable: the list payload omits the {@code goods} column,
 * so before this a client could only tell a substantial topic from an empty one by opening each
 * topic's detail (see handoff-topic-goods-count.md).
 */
public class TopicQueryServiceTest {

    private final LitemallTopicService topicService = mock(LitemallTopicService.class);
    private final LitemallGoodsService goodsService = mock(LitemallGoodsService.class);
    private final TopicQueryService service = new TopicQueryService(topicService, goodsService);

    private static LitemallTopic topic(int id) {
        LitemallTopic topic = new LitemallTopic();
        topic.setId(id);
        return topic;
    }

    @Test
    public void listAttachesGoodsCountsAndReturnsThePageItself() {
        // A real PageHelper Page: ResponseUtil.okList reads total/page/limit/pages off this
        // instance, so list() must hand back the very object queryList produced. Rebuilding the
        // rows to bolt a count on would silently collapse total to the page size.
        Page<LitemallTopic> page = new Page<>(1, 10);
        page.add(topic(7001));
        page.add(topic(7002));
        page.setTotal(37);

        when(topicService.queryList(anyInt(), anyInt(), anyString(), anyString())).thenReturn(page);
        doAnswer(invocation -> {
            List<LitemallTopic> rows = invocation.getArgument(0);
            int count = 3;
            for (LitemallTopic row : rows) {
                row.setGoodsCount(count--);
            }
            return null;
        }).when(topicService).attachGoodsCounts(any());

        List<LitemallTopic> result = service.list(1, 10, "add_time", "desc");

        assertSame(page, result, "the PageHelper page must be returned, not a copy");
        assertEquals(37, ((Page<?>) result).getTotal(), "total survives the attach");
        assertEquals(3, result.get(0).getGoodsCount());
        assertEquals(2, result.get(1).getGoodsCount());
    }
}
