package org.linlinjava.litemall.order.infrastructure.services.acl.facades;

import org.linlinjava.litemall.order.application.util.exception.cj.LitemallCjOrderException;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjOrderPlacement;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjOrderResult;
import org.linlinjava.litemall.order.infrastructure.services.cj.CjTokenService;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.CjOrderFeignClient;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjCreateOrderRequest;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjCreateOrderResponse;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjOrderProduct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link CjDropshipOrderFacade} implementation: authenticates via {@link CjTokenService}, maps a
 * {@link CjOrderPlacement} to the CJ {@code createOrder} request, calls {@link CjOrderFeignClient},
 * and converts any failure (transport, circuit-open, or {@code result=false}) into a
 * {@link LitemallCjOrderException}. Mirrors {@code LitemallGoodsFacadeImpl.call(...)}.
 */
@Component
public class CjDropshipOrderFacadeImpl implements CjDropshipOrderFacade {

    private static final Logger log = LoggerFactory.getLogger(CjDropshipOrderFacadeImpl.class);

    private final CjOrderFeignClient cjOrderFeignClient;
    private final CjTokenService cjTokenService;

    public CjDropshipOrderFacadeImpl(CjOrderFeignClient cjOrderFeignClient, CjTokenService cjTokenService) {
        this.cjOrderFeignClient = cjOrderFeignClient;
        this.cjTokenService = cjTokenService;
    }

    @Override
    public CjOrderResult placeOrder(CjOrderPlacement placement) {
        if (placement == null || placement.getLines() == null || placement.getLines().isEmpty()) {
            throw new LitemallCjOrderException("no CJ lines to place");
        }
        CjCreateOrderRequest request = toRequest(placement);

        CjCreateOrderResponse response;
        try {
            String token = cjTokenService.getValidToken();
            response = cjOrderFeignClient.createOrder(token, request);
        } catch (RuntimeException e) {
            // Transport errors (connect/read timeout, 5xx) and auth failures surface here.
            log.error("CJ create-order transport/auth failure for orderNumber={}", placement.getOrderNumber(), e);
            throw new LitemallCjOrderException("transport/auth failure", e);
        }

        if (response == null || !response.isResult() || response.getData() == null) {
            String message = response == null ? "null response" : response.getMessage();
            log.error("CJ create-order rejected for orderNumber={}: {}", placement.getOrderNumber(), message);
            throw new LitemallCjOrderException(message);
        }

        CjCreateOrderResponse.Data data = response.getData();
        return new CjOrderResult(data.getOrderId(), data.getOrderNum(), data.getOrderStatus());
    }

    private CjCreateOrderRequest toRequest(CjOrderPlacement p) {
        List<CjOrderProduct> products = p.getLines().stream()
                .map(l -> new CjOrderProduct(l.getVid(), l.getQuantity()))
                .collect(Collectors.toList());
        return CjCreateOrderRequest.builder()
                .orderNumber(p.getOrderNumber())
                .shippingCustomerName(p.getCustomerName())
                .shippingPhone(p.getPhone())
                .shippingCountryCode(p.getCountryCode())
                .shippingCountry(p.getCountry())
                .shippingProvince(p.getProvince())
                .shippingCity(p.getCity())
                .shippingAddress(p.getAddress())
                .shippingZip(p.getZip())
                .remark(p.getRemark())
                .products(products)
                .build();
    }
}
