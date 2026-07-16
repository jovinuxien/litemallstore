package org.linlinjava.litemall.promotion.infrastructure.services;

import org.linlinjava.litemall.db.dao.LitemallSeckillMapper;
import org.linlinjava.litemall.db.domain.LitemallGoods;
import org.linlinjava.litemall.db.domain.LitemallSeckill;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.promotion.application.ports.SocialCatalogPort;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.ApiResponse;
import org.linlinjava.litemall.promotion.infrastructure.services.feignclients.GoodsServiceFeignClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@link SocialCatalogPort} over the shared litemall-db read model (goods facts
 * + the V38 price-swap deal state) plus the goods-service video endpoint. The
 * video lookup is fail-soft: goods-service down, non-CJ goods, or no videos all
 * degrade to {@code videoUrl=null}, which the composer/adapters surface as the
 * TikTok video gate — never an exception through the admin surface.
 */
@Service
public class SocialCatalogAdapter implements SocialCatalogPort {

    private static final Logger logger = LoggerFactory.getLogger(SocialCatalogAdapter.class);

    private final LitemallGoodsService goodsService;
    private final LitemallSeckillMapper seckillMapper;
    private final GoodsServiceFeignClient goodsServiceFeignClient;

    public SocialCatalogAdapter(LitemallGoodsService goodsService,
                                LitemallSeckillMapper seckillMapper,
                                GoodsServiceFeignClient goodsServiceFeignClient) {
        this.goodsService = goodsService;
        this.seckillMapper = seckillMapper;
        this.goodsServiceFeignClient = goodsServiceFeignClient;
    }

    @Override
    public Optional<GoodsSocialSnapshot> goodsSnapshot(Integer goodsId) {
        if (goodsId == null) {
            return Optional.empty();
        }
        LitemallGoods goods = goodsService.findById(goodsId);
        if (goods == null) {
            return Optional.empty();
        }
        List<String> gallery = goods.getGallery() != null
                ? List.of(goods.getGallery()).stream()
                        .filter(StringUtils::hasText)
                        .collect(Collectors.toList())
                : new ArrayList<>();
        return Optional.of(new GoodsSocialSnapshot(
                goods.getId(),
                goods.getName(),
                goods.getRetailPrice(),
                goods.getCounterPrice(),
                goods.getPicUrl(),
                gallery,
                firstVideoUrl(goodsId)));
    }

    @Override
    public Optional<LiveDeal> liveDealFor(Integer goodsId) {
        if (goodsId == null) {
            return Optional.empty();
        }
        LitemallSeckill deal = seckillMapper.selectLiveByGoodsId(goodsId);
        return deal != null ? Optional.of(toLiveDeal(deal)) : Optional.empty();
    }

    @Override
    public List<LiveDeal> liveDeals() {
        return seckillMapper.selectSwapped().stream()
                .map(this::toLiveDeal)
                .collect(Collectors.toList());
    }

    /** First live product video's URL, or null (fail-soft — videos are a bonus surface). */
    private String firstVideoUrl(Integer goodsId) {
        try {
            ApiResponse<List<Map<String, Object>>> response =
                    goodsServiceFeignClient.getGoodsVideos(String.valueOf(goodsId));
            if (response == null || response.getErrno() != 0 || response.getData() == null) {
                return null;
            }
            return response.getData().stream()
                    .map(video -> video.get("url"))
                    .filter(url -> url instanceof String && StringUtils.hasText((String) url))
                    .map(url -> (String) url)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            logger.warn("goods-service video lookup failed for goods {}: {}", goodsId, e.getMessage());
            return null;
        }
    }

    private LiveDeal toLiveDeal(LitemallSeckill deal) {
        return new LiveDeal(
                deal.getId(),
                deal.getGoodsId(),
                deal.getPrice(),
                deal.getOriginalRetailPrice(),
                deal.getStartTime(),
                deal.getStopTime());
    }
}
