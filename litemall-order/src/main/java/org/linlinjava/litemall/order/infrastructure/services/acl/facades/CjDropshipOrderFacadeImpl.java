package org.linlinjava.litemall.order.infrastructure.services.acl.facades;

import org.linlinjava.litemall.order.application.util.exception.cj.LitemallCjDisabledException;
import org.linlinjava.litemall.order.application.util.exception.cj.LitemallCjOrderException;
import org.linlinjava.litemall.order.application.util.exception.cj.LitemallCjRetryableException;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjBalance;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjLogisticsOption;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjOrderPlacement;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjOrderResult;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjOrderSnapshot;
import org.linlinjava.litemall.order.infrastructure.services.cj.CjTokenService;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.CjOrderFeignClient;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjBalanceResponse;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjCreateOrderRequest;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjCreateOrderResponse;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjFreightCalculateRequest;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjFreightCalculateResponse;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjOrderDetailResponse;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjOrderIdRequest;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjOrderProduct;
import org.linlinjava.litemall.order.infrastructure.services.feignclients.cj.dto.CjSimpleResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@link CjDropshipOrderFacade} implementation: authenticates via {@link CjTokenService}, maps a
 * {@link CjOrderPlacement} to the CJ {@code createOrderV2} request (payType=3 create-only draft,
 * sandbox flag config-driven), calls {@link CjOrderFeignClient}, and classifies every placement
 * failure for the retained-placement machinery (Wave 8):
 * <ul>
 *   <li>CJ disabled (no credentials) → {@link LitemallCjDisabledException}, rethrown untouched;</li>
 *   <li>transport / auth / circuit-open / CJ rate-limit → {@link LitemallCjRetryableException}
 *       (the placement sweep retries indefinitely — the paid order is never stranded);</li>
 *   <li>a CJ business rejection ({@code result=false}) → the base
 *       {@link LitemallCjOrderException}, which the placement path treats as TERMINAL.</li>
 * </ul>
 * Lifecycle operations (confirm / payBalance / detail / delete / balance) are best-effort per
 * the facade contract. Mirrors {@code LitemallGoodsFacadeImpl.call(...)}.
 */
@Component
public class CjDropshipOrderFacadeImpl implements CjDropshipOrderFacade {

    private static final Logger log = LoggerFactory.getLogger(CjDropshipOrderFacadeImpl.class);

    /** createOrderV2 payType: create-only draft — never move CJ money inside the pay transaction. */
    private static final int PAY_TYPE_CREATE_ONLY = 3;

    private final CjOrderFeignClient cjOrderFeignClient;
    private final CjTokenService cjTokenService;
    /** CJ ship-from warehouse country. CJ createOrder rejects a missing one (code 1600300). */
    private final String fromCountryCode;
    /** CJ logistics line. CJ createOrder also requires this (code 1600300 "logisticName must be not empty"). */
    private final String logisticName;
    /** When true, createOrderV2 carries isSandbox=1 so CJ simulates payment (no real charges). */
    private final boolean sandbox;
    /**
     * IOSS declaration for EU destinations (createOrderV2 rejects EU orders without one,
     * error 100104/7001). 0 (default) = OMIT the field so CJ falls back to the account's
     * dashboard IOSS Option ("Declare with CJ's IOSS" / own IOSS per country) — CJ's
     * documented behavior when no IOSS is supplied on the order. 1/2/3 send explicitly
     * (2 requires ioss-number). See the ADR.
     */
    private final int iossType;
    /** Merchant IOSS number, sent when non-blank (required with {@code ioss-type: 2}). */
    private final String iossNumber;
    /** Store contact used when the ordering customer has no email on file (CJ error 3001). */
    private final String customerEmailFallback;

    public CjDropshipOrderFacadeImpl(CjOrderFeignClient cjOrderFeignClient, CjTokenService cjTokenService,
                                     @org.springframework.beans.factory.annotation.Value("${spring.cjdropship.api.from-country-code:CN}") String fromCountryCode,
                                     @org.springframework.beans.factory.annotation.Value("${spring.cjdropship.api.logistic-name:CJPacket Ordinary}") String logisticName,
                                     @org.springframework.beans.factory.annotation.Value("${spring.cjdropship.api.sandbox:false}") boolean sandbox,
                                     @org.springframework.beans.factory.annotation.Value("${spring.cjdropship.api.ioss-type:0}") int iossType,
                                     @org.springframework.beans.factory.annotation.Value("${spring.cjdropship.api.ioss-number:}") String iossNumber,
                                     @org.springframework.beans.factory.annotation.Value("${spring.cjdropship.api.customer-email-fallback:orders@litemall.dev}") String customerEmailFallback) {
        this.cjOrderFeignClient = cjOrderFeignClient;
        this.cjTokenService = cjTokenService;
        this.fromCountryCode = fromCountryCode;
        this.logisticName = logisticName;
        this.sandbox = sandbox;
        this.iossType = iossType;
        this.iossNumber = iossNumber;
        this.customerEmailFallback = customerEmailFallback;
    }

    @Override
    public CjOrderResult placeOrder(CjOrderPlacement placement) {
        if (placement == null || placement.getLines() == null || placement.getLines().isEmpty()) {
            throw new LitemallCjOrderException("no CJ lines to place");
        }

        CjCreateOrderResponse response;
        String placedLogistic = logisticName;
        try {
            String token = cjTokenService.getValidToken();
            List<CjOrderProduct> products = toProducts(placement);
            // createOrder only accepts a logisticName that freightCalculate offers for this
            // product/destination combination (else code 1605001) — resolve it live, falling
            // back to the configured default only when the freight call itself fails.
            CjLogisticsOption option = resolveLogisticOption(token, placement.getCountryCode(), products);
            String logistic = option != null ? option.getLogisticName() : logisticName;
            CjCreateOrderRequest request = toRequest(placement, products, logistic);
            response = cjOrderFeignClient.createOrderV2(token, request);
            placedLogistic = logistic;
        } catch (LitemallCjDisabledException e) {
            // No credentials — no call was made; the placement sweep retains the order.
            throw e;
        } catch (RuntimeException e) {
            // Transport/auth errors AND HTTP-level failures (the FeignErrorDecoder turns an error
            // body into an exception, erasing the HTTP status — so everything surfacing here is
            // classified RETRYABLE, the safe direction). Unwrap to the root cause so the real CJ
            // message (e.g. "fromCountryCode must not be empty") reaches the caller instead of a
            // generic label.
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            String detail = root.getMessage() != null ? root.getMessage() : "transport/auth failure";
            log.error("CJ create-order failed for orderNumber={}: {}", placement.getOrderNumber(), detail, e);
            throw new LitemallCjRetryableException(detail, e);
        }

        // Decide acceptance from CJ's own result/code flag — NOT from data being present.
        // CJ returns result=true,message="Success" on acceptance but is inconsistent about the
        // shape of data (object vs a bare orderId string vs empty); gating success on data!=null
        // wrongly rejected genuinely-placed orders ("rejected ... : Success").
        boolean accepted = response != null && (response.isResult() || response.getCode() == 200);
        if (!accepted) {
            String message = response == null ? "null response" : response.getMessage();
            log.error("CJ create-order rejected for orderNumber={}: {}", placement.getOrderNumber(), message);
            if (message != null && isRateLimitMessage(message)) {
                throw new LitemallCjRetryableException(message);
            }
            // A CJ business rejection (bad vid, bad address, IOSS…) — retrying cannot fix it.
            throw new LitemallCjOrderException(message);
        }

        CjCreateOrderResponse.Data data = response.getData();
        if (data == null) {
            // CJ accepted the order (result=true) but returned data in a shape we could not bind.
            // The order likely EXISTS at CJ — but recording a placement without a cj_order_id
            // produces a stuck order no lifecycle/cancel/tracking guard can touch. Classify
            // retryable instead: the placement sweep reconciles by our merchant orderNumber
            // (getOrderDetail accepts it) and adopts the CJ-side order without re-creating it.
            log.warn("CJ create-order accepted orderNumber={} but returned no parseable data object; "
                    + "deferring to reconcile-by-orderNumber next sweep", placement.getOrderNumber());
            throw new LitemallCjRetryableException("CJ accepted orderNumber=" + placement.getOrderNumber()
                    + " but returned no parseable data; will reconcile by orderNumber");
        }
        return new CjOrderResult(data.getOrderId(), data.getOrderNum(), data.getOrderStatus(), placedLogistic);
    }

    /** CJ rate-limit / quota answers are transient — the sweep retries them. */
    private static boolean isRateLimitMessage(String message) {
        String m = message.toLowerCase(java.util.Locale.ROOT);
        return m.contains("too many request") || m.contains("frequent") || m.contains("quota")
                || m.contains("rate limit") || m.contains("try again later");
    }

    private List<CjOrderProduct> toProducts(CjOrderPlacement p) {
        return p.getLines().stream()
                .map(l -> new CjOrderProduct(l.getVid(), l.getQuantity()))
                .collect(Collectors.toList());
    }

    @Override
    public CjLogisticsOption quoteLogistics(String endCountryCode, List<CjOrderPlacement.Line> lines) {
        if (endCountryCode == null || endCountryCode.isBlank() || lines == null || lines.isEmpty()) {
            return null;
        }
        try {
            String token = cjTokenService.getValidToken();
            List<CjOrderProduct> products = lines.stream()
                    .map(l -> new CjOrderProduct(l.getVid(), l.getQuantity()))
                    .collect(Collectors.toList());
            return resolveLogisticOption(token, endCountryCode, products);
        } catch (RuntimeException e) {
            log.warn("CJ logistics quote failed for {}->{}: {}", fromCountryCode, endCountryCode, e.getMessage());
            return null;
        }
    }

    /**
     * Pick the logistics line for this shipment from CJ freightCalculate: the configured
     * default when CJ offers it, else the cheapest offered line. Any freight-call failure
     * (outage, breaker open, empty offer list) yields {@code null} — placement falls back to
     * the configured default name and lets createOrder be the arbiter.
     */
    private CjLogisticsOption resolveLogisticOption(String token, String endCountryCode, List<CjOrderProduct> products) {
        try {
            CjFreightCalculateResponse freight = cjOrderFeignClient.freightCalculate(token,
                    CjFreightCalculateRequest.builder()
                            .startCountryCode(fromCountryCode)
                            .endCountryCode(endCountryCode)
                            .products(products)
                            .build());
            List<CjFreightCalculateResponse.Option> options =
                    freight != null && (freight.isResult() || freight.getCode() == 200) && freight.getData() != null
                            ? freight.getData() : List.of();
            for (CjFreightCalculateResponse.Option option : options) {
                if (logisticName.equalsIgnoreCase(option.getLogisticName())) {
                    return toOption(option);
                }
            }
            java.util.Optional<CjFreightCalculateResponse.Option> cheapest = options.stream()
                    .filter(o -> o.getLogisticName() != null && !o.getLogisticName().isBlank())
                    .min(java.util.Comparator.comparing(
                            o -> o.getLogisticPrice() == null
                                    ? new java.math.BigDecimal(Long.MAX_VALUE) : o.getLogisticPrice()));
            if (cheapest.isPresent()) {
                log.info("CJ freightCalculate for {}->{}: using '{}' ({} {})", fromCountryCode,
                        endCountryCode, cheapest.get().getLogisticName(),
                        cheapest.get().getLogisticPrice(), cheapest.get().getLogisticAging());
                return toOption(cheapest.get());
            }
            log.warn("CJ freightCalculate offered no logistics for {}->{} ({}); using configured default '{}'",
                    fromCountryCode, endCountryCode,
                    freight == null ? "null response" : freight.getMessage(), logisticName);
        } catch (RuntimeException e) {
            log.warn("CJ freightCalculate failed for {}->{}; using configured default '{}': {}",
                    fromCountryCode, endCountryCode, logisticName, e.getMessage());
        } finally {
            // CJ's API is rate-limited (~1 QPS account-wide); pace the follow-up call
            // (createOrder, or the next quote) so the freight call doesn't earn it a 429.
            try {
                Thread.sleep(1100);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        return null;
    }

    private static CjLogisticsOption toOption(CjFreightCalculateResponse.Option o) {
        return new CjLogisticsOption(o.getLogisticName(), o.getLogisticPrice(), o.getLogisticAging());
    }

    private CjCreateOrderRequest toRequest(CjOrderPlacement p, List<CjOrderProduct> products, String logistic) {
        return CjCreateOrderRequest.builder()
                .orderNumber(p.getOrderNumber())
                .fromCountryCode(fromCountryCode)
                .logisticName(logistic)
                .shippingCustomerName(p.getCustomerName())
                .shippingPhone(p.getPhone())
                .shippingCountryCode(p.getCountryCode())
                .shippingCountry(p.getCountry())
                .shippingProvince(p.getProvince())
                .shippingCity(p.getCity())
                .shippingAddress(p.getAddress())
                .shippingZip(p.getZip())
                .remark(p.getRemark())
                .payType(PAY_TYPE_CREATE_ONLY)
                .isSandbox(sandbox ? 1 : null)
                .iossType(iossType > 0 ? iossType : null)
                .iossNumber(org.springframework.util.StringUtils.hasText(iossNumber) ? iossNumber.trim() : null)
                .email(org.springframework.util.StringUtils.hasText(p.getEmail())
                        ? p.getEmail().trim() : customerEmailFallback)
                .products(products)
                .build();
    }

    @Override
    public boolean confirmOrder(String cjOrderId) {
        return simpleCall("confirmOrder", cjOrderId,
                token -> cjOrderFeignClient.confirmOrder(token, new CjOrderIdRequest(cjOrderId)));
    }

    @Override
    public boolean payBalance(String cjOrderId) {
        return simpleCall("payBalance", cjOrderId,
                token -> cjOrderFeignClient.payBalance(token, new CjOrderIdRequest(cjOrderId)));
    }

    @Override
    public boolean deleteOrder(String cjOrderId) {
        return simpleCall("deleteOrder", cjOrderId,
                token -> cjOrderFeignClient.deleteOrder(token, cjOrderId));
    }

    @Override
    public Optional<CjOrderSnapshot> fetchOrderDetail(String cjOrderId) {
        if (cjOrderId == null || cjOrderId.isBlank()) {
            return Optional.empty();
        }
        try {
            String token = cjTokenService.getValidToken();
            CjOrderDetailResponse response = cjOrderFeignClient.getOrderDetail(token, cjOrderId);
            boolean accepted = response != null && (response.isResult() || response.getCode() == 200);
            if (!accepted || response.getData() == null) {
                log.warn("CJ getOrderDetail gave no usable answer for {}: {}", cjOrderId,
                        response == null ? "null response" : response.getMessage());
                return Optional.empty();
            }
            CjOrderDetailResponse.Data d = response.getData();
            return Optional.of(new CjOrderSnapshot(d.getOrderId(), d.getOrderStatus(), d.getSubStatus(),
                    d.getTrackNumber(), d.getTrackingProvider(), d.getLogisticName()));
        } catch (RuntimeException e) {
            log.warn("CJ getOrderDetail failed for {}: {}", cjOrderId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<CjBalance> getBalance() {
        try {
            String token = cjTokenService.getValidToken();
            CjBalanceResponse response = cjOrderFeignClient.getBalance(token);
            boolean accepted = response != null && (response.isResult() || response.getCode() == 200);
            if (!accepted || response.getData() == null) {
                log.warn("CJ getBalance gave no usable answer: {}",
                        response == null ? "null response" : response.getMessage());
                return Optional.empty();
            }
            CjBalanceResponse.Data d = response.getData();
            return Optional.of(new CjBalance(d.getAmount(), d.getNoWithdrawalAmount(), d.getFreezeAmount()));
        } catch (RuntimeException e) {
            log.warn("CJ getBalance failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Shared shape of the best-effort lifecycle mutations (confirm / payBalance / delete):
     * acceptance decided from {@code result}/{@code code} exactly like placement; any failure
     * logs and returns {@code false} so the caller (post-commit hook or poller) retries later.
     */
    private boolean simpleCall(String operation, String cjOrderId,
                               java.util.function.Function<String, CjSimpleResponse> call) {
        if (cjOrderId == null || cjOrderId.isBlank()) {
            return false;
        }
        try {
            String token = cjTokenService.getValidToken();
            CjSimpleResponse response = call.apply(token);
            boolean accepted = response != null && (response.isResult() || response.getCode() == 200);
            if (!accepted) {
                log.warn("CJ {} rejected for {}: {}", operation, cjOrderId,
                        response == null ? "null response" : response.getMessage());
            }
            return accepted;
        } catch (RuntimeException e) {
            log.warn("CJ {} failed for {}: {}", operation, cjOrderId, e.getMessage());
            return false;
        }
    }
}
