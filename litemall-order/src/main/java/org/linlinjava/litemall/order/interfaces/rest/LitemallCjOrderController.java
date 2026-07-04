package org.linlinjava.litemall.order.interfaces.rest;

import org.linlinjava.litemall.order.application.internal.cj.CjOrderLineResolver;
import org.linlinjava.litemall.order.application.util.exception.cj.LitemallCjOrderException;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.CjDropshipOrderFacade;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjOrderPlacement;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.cj.CjOrderResult;
import org.linlinjava.litemall.order.interfaces.dtos.cj.CjOrderRequestDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Internal callable contract for placing a CJ Dropshipping order. A CJ failure surfaces as
 * HTTP 502 with the CJ message (no half-placed order).
 *
 * @deprecated Superseded by the pay-first flow: CJ items now go through the normal
 * {@code /srv/order/submit} (order tagged {@code source='cj'}) and are replayed to CJ
 * createOrder when {@code /actions/pay} succeeds (see {@code CjFulfillmentService}), so
 * they appear in My Orders and are paid before placement. This direct endpoint bypasses
 * payment AND persists nothing locally; it stays only until the customer SPA switches to
 * submit→pay for CJ items (gateway-api follow-up), then it is deleted.
 */
@Deprecated
@RestController
@RequestMapping("/srv/order/cj")
public class LitemallCjOrderController {

    private final CjDropshipOrderFacade cjOrderFacade;
    private final CjOrderLineResolver lineResolver;

    public LitemallCjOrderController(CjDropshipOrderFacade cjOrderFacade, CjOrderLineResolver lineResolver) {
        this.cjOrderFacade = cjOrderFacade;
        this.lineResolver = lineResolver;
    }

    @PostMapping("/orders")
    public ResponseEntity<?> placeCjOrder(@RequestBody CjOrderRequestDto dto) {
        try {
            CjOrderResult result = cjOrderFacade.placeOrder(toPlacement(dto));
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (LitemallCjOrderException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    private CjOrderPlacement toPlacement(CjOrderRequestDto dto) {
        List<CjOrderPlacement.Line> lines = dto.getLines() == null ? List.of()
                : dto.getLines().stream()
                .map(l -> CjOrderPlacement.Line.builder().vid(resolveVid(l)).quantity(l.getQuantity()).build())
                .collect(Collectors.toList());
        return CjOrderPlacement.builder()
                .orderNumber(dto.getOrderNumber())
                .customerName(dto.getCustomerName())
                .phone(dto.getPhone())
                .countryCode(dto.getCountryCode())
                .country(dto.getCountry())
                .province(dto.getProvince())
                .city(dto.getCity())
                .address(dto.getAddress())
                .zip(dto.getZip())
                .remark(dto.getRemark())
                .lines(lines)
                .build();
    }

    /**
     * Recover the CJ {@code cj_vid} for a line: prefer the native {@code productId} (server reads
     * {@code cj_vid} off the row and confirms {@code source='cj'}); fall back to an explicitly-supplied
     * {@code vid} for internal callers that already hold it.
     */
    private String resolveVid(CjOrderRequestDto.Line line) {
        if (line.getProductId() != null) {
            return lineResolver.resolveVid(line.getProductId());
        }
        if (line.getVid() != null && !line.getVid().isBlank()) {
            return line.getVid();
        }
        throw new LitemallCjOrderException("CJ line needs a productId (or an explicit vid)");
    }
}
