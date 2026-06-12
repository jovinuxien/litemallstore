package org.linlinjava.litemall.order.interfaces.rest;

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
 * Internal callable contract for placing a CJ Dropshipping order. This is the order-side surface
 * the handoff named; the (future) checkout-routing will instead call {@link CjDropshipOrderFacade}
 * directly for {@code cj_}-sourced lines. A CJ failure surfaces as HTTP 502 with the CJ message
 * (no half-placed order).
 */
@RestController
@RequestMapping("/srv/order/cj")
public class LitemallCjOrderController {

    private final CjDropshipOrderFacade cjOrderFacade;

    public LitemallCjOrderController(CjDropshipOrderFacade cjOrderFacade) {
        this.cjOrderFacade = cjOrderFacade;
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
                .map(l -> CjOrderPlacement.Line.builder().vid(l.getVid()).quantity(l.getQuantity()).build())
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
}
