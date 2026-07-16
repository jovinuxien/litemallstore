package org.linlinjava.litemall.promotion.infrastructure.services.feignclients;

import org.linlinjava.litemall.promotion.domain.model.valueobjects.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "goods-service", url = "${goods.service.url}")
public interface GoodsServiceFeignClient {

    @GetMapping("/goods/{goodsId}")
    ApiResponse<?> getGoods(@PathVariable Integer goodsId);

    /**
     * Wave-3 CJ product videos for a goods ({@code [{id, name, url, duration, …}]});
     * empty list for non-CJ goods or CJ down. Feeds the social composer's TikTok
     * video gate (Wave 6).
     */
    @GetMapping("/srv/goods/videos")
    ApiResponse<List<Map<String, Object>>> getGoodsVideos(@RequestParam("id") String goodsId);
}
