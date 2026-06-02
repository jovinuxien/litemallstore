package org.linlinjava.litemall.promotion.infrastructure.services.acl;

import org.linlinjava.litemall.promotion.application.ports.CustomerStatisticsProvider;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.ApiResponse;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.LitemallUserId;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.CustomerStatistics;
import org.linlinjava.litemall.promotion.infrastructure.services.feignclients.OrderStatisticsFeignClient;
import org.linlinjava.litemall.promotion.infrastructure.services.feignclients.dto.CustomerRfmStatDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Anti-corruption layer over {@link OrderStatisticsFeignClient}: translates the
 * order service's RFM DTOs into domain {@link CustomerStatistics} value objects
 * and isolates wire/transport concerns from the targeting engine. The default
 * {@link CustomerStatisticsProvider}; a Matomo adapter swaps in for Phase 3.
 *
 * <p>Degrades gracefully — a transport failure or empty/malformed payload yields
 * an empty population (logged), so an evaluation produces an empty audience
 * rather than throwing through the admin endpoint.
 */
@Component
public class OrderStatisticsAdapter implements CustomerStatisticsProvider {

    private static final Logger logger = LoggerFactory.getLogger(OrderStatisticsAdapter.class);

    private final OrderStatisticsFeignClient orderStatisticsFeignClient;

    public OrderStatisticsAdapter(OrderStatisticsFeignClient orderStatisticsFeignClient) {
        this.orderStatisticsFeignClient = orderStatisticsFeignClient;
    }

    @Override
    public List<CustomerStatistics> fetchSince(LocalDateTime since) {
        String sinceIso = since.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        try {
            ApiResponse<List<CustomerRfmStatDto>> response =
                    orderStatisticsFeignClient.getCustomerRfm(sinceIso);
            if (response == null || response.getData() == null) {
                logger.warn("Order RFM stats response empty (since={})", sinceIso);
                return new ArrayList<>();
            }
            List<CustomerStatistics> result = new ArrayList<>();
            for (CustomerRfmStatDto dto : response.getData()) {
                CustomerStatistics stats = toDomain(dto);
                if (stats != null) {
                    result.add(stats);
                }
            }
            return result;
        } catch (Exception e) {
            logger.error("Failed to fetch customer RFM stats from order service (since={}): {}",
                    sinceIso, e.getMessage());
            return new ArrayList<>();
        }
    }

    private CustomerStatistics toDomain(CustomerRfmStatDto dto) {
        if (dto == null || dto.getUserId() == null || dto.getUserId() <= 0) {
            return null;
        }
        BigDecimal spend = dto.getTotalSpend() != null ? dto.getTotalSpend() : BigDecimal.ZERO;
        if (spend.compareTo(BigDecimal.ZERO) < 0) {
            spend = BigDecimal.ZERO;
        }
        int orders = dto.getOrderCount() != null ? dto.getOrderCount() : 0;
        return new CustomerStatistics(
                new LitemallUserId(dto.getUserId()),
                dto.getLastOrderAt(),
                orders,
                new LitemallMoney(spend));
    }
}
