package org.linlinjava.litemall.order.infrastructure.services.acl.facades;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjTrackingSnapshot;
import org.linlinjava.litemall.order.infrastructure.services.cj.CjTokenService;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.CjTrackingFeignClient;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.tracking.CjTrackInfoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * {@link CjTrackingFacade} implementation over {@code logistic/trackInfo}. Answers are cached
 * for 1 hour per tracking number (Caffeine, negative results included — the
 * {@code CjFreightQuoteService} pattern): tracking moves slowly, tracking pages re-render often,
 * and CJ enforces ~1 QPS account-wide, so a fresh call per render is neither needed nor allowed.
 */
@Component
public class CjTrackingFacadeImpl implements CjTrackingFacade {

    private static final Logger log = LoggerFactory.getLogger(CjTrackingFacadeImpl.class);

    private final CjTrackingFeignClient trackingFeignClient;
    private final CjTokenService cjTokenService;
    private final Cache<String, Optional<CjTrackingSnapshot>> cache = Caffeine.newBuilder()
            .maximumSize(5000)
            .expireAfterWrite(Duration.ofHours(1))
            .build();

    public CjTrackingFacadeImpl(CjTrackingFeignClient trackingFeignClient, CjTokenService cjTokenService) {
        this.trackingFeignClient = trackingFeignClient;
        this.cjTokenService = cjTokenService;
    }

    @Override
    public Optional<CjTrackingSnapshot> trackInfo(String trackNumber) {
        if (trackNumber == null || trackNumber.isBlank()) {
            return Optional.empty();
        }
        return cache.get(trackNumber.trim(), this::query);
    }

    private Optional<CjTrackingSnapshot> query(String trackNumber) {
        try {
            String token = cjTokenService.getValidToken();
            CjTrackInfoResponse response = trackingFeignClient.trackInfo(token, trackNumber);
            boolean usable = response != null && (response.isResult() || response.getCode() == 200);
            if (!usable || response.getData() == null || response.getData().isEmpty()) {
                log.info("CJ trackInfo has nothing for {} ({})", trackNumber,
                        response == null ? "null response" : response.getMessage());
                return Optional.empty();
            }
            CjTrackInfoResponse.TrackInfo t = response.getData().get(0);
            return Optional.of(new CjTrackingSnapshot(
                    t.getTrackingNumber() != null ? t.getTrackingNumber() : trackNumber,
                    t.getLogisticName(), t.getTrackingFrom(), t.getTrackingTo(),
                    t.getDeliveryDay(), t.getDeliveryTime(), t.getTrackingStatus(),
                    t.getLastMileCarrier(), t.getLastTrackNumber()));
        } catch (RuntimeException e) {
            log.warn("CJ trackInfo failed for {}: {}", trackNumber, e.getMessage());
            return Optional.empty();
        }
    }
}
