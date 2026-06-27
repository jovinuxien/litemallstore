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
    /** CJ ship-from warehouse country. CJ createOrder rejects a missing one (code 1600300). */
    private final String fromCountryCode;
    /** CJ logistics line. CJ createOrder also requires this (code 1600300 "logisticName must be not empty"). */
    private final String logisticName;

    public CjDropshipOrderFacadeImpl(CjOrderFeignClient cjOrderFeignClient, CjTokenService cjTokenService,
                                     @org.springframework.beans.factory.annotation.Value("${spring.cjdropship.api.from-country-code:CN}") String fromCountryCode,
                                     @org.springframework.beans.factory.annotation.Value("${spring.cjdropship.api.logistic-name:CJPacket Ordinary}") String logisticName) {
        this.cjOrderFeignClient = cjOrderFeignClient;
        this.cjTokenService = cjTokenService;
        this.fromCountryCode = fromCountryCode;
        this.logisticName = logisticName;
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
            // Transport/auth errors AND CJ business rejections (the FeignErrorDecoder turns a CJ
            // error body into an exception) surface here. Unwrap to the root cause so the real CJ
            // message (e.g. "fromCountryCode must not be empty") reaches the caller instead of a
            // generic label.
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            String detail = root.getMessage() != null ? root.getMessage() : "transport/auth failure";
            log.error("CJ create-order failed for orderNumber={}: {}", placement.getOrderNumber(), detail, e);
            throw new LitemallCjOrderException(detail, e);
        }

        // Decide acceptance from CJ's own result/code flag — NOT from data being present.
        // CJ returns result=true,message="Success" on acceptance but is inconsistent about the
        // shape of data (object vs a bare orderId string vs empty); gating success on data!=null
        // wrongly rejected genuinely-placed orders ("rejected ... : Success").
        boolean accepted = response != null && (response.isResult() || response.getCode() == 200);
        if (!accepted) {
            String message = response == null ? "null response" : response.getMessage();
            log.error("CJ create-order rejected for orderNumber={}: {}", placement.getOrderNumber(), message);
            throw new LitemallCjOrderException(message);
        }

        CjCreateOrderResponse.Data data = response.getData();
        if (data == null) {
            // CJ accepted the order (result=true) but returned data in a shape we could not bind
            // (e.g. an empty/blank value). The order IS placed — surface success without the CJ id
            // rather than failing the customer; our merchant orderNumber remains the idempotency key.
            log.warn("CJ create-order accepted orderNumber={} but returned no parseable data object; "
                    + "proceeding without CJ order id", placement.getOrderNumber());
            return new CjOrderResult(null, null, null);
        }
        return new CjOrderResult(data.getOrderId(), data.getOrderNum(), data.getOrderStatus());
    }

    private CjCreateOrderRequest toRequest(CjOrderPlacement p) {
        List<CjOrderProduct> products = p.getLines().stream()
                .map(l -> new CjOrderProduct(l.getVid(), l.getQuantity()))
                .collect(Collectors.toList());
        return CjCreateOrderRequest.builder()
                .orderNumber(p.getOrderNumber())
                .fromCountryCode(fromCountryCode)
                .logisticName(logisticName)
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
