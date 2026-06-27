package org.linlinjava.litemall.order.interfaces.rest;

import lombok.extern.slf4j.Slf4j;
import org.linlinjava.litemall.order.application.internal.LitemallAddressServiceLayer;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallAddressAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.ApiResponse;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallAddressId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.interfaces.dtos.address.AddressDtoResponse;
import org.linlinjava.litemall.order.interfaces.dtos.address.AddressSaveRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Customer address-book edge. The acting user is taken from the gateway-trusted
 * {@code X-User-Id} header (as in {@link LitemallOrderRestController} /
 * {@link LitemallWalletRestController}), never from a request-supplied param — so a
 * caller can only ever touch their own address book (no caller-supplied {@code userId},
 * avoiding the cart IDOR pattern). A saved {@code addressId} produced here flows
 * straight into {@code POST /srv/order/submit}.
 *
 * <p>NOTE: {@code /srv/address/**} must be added to the gateway-api {@code customer-order}
 * route predicate so it reaches the order service rather than falling through to
 * goods-management — tracked as a gateway-api follow-up.
 */
@RestController
@RequestMapping("/srv/address")
@Slf4j
public class LitemallAddressController {

    private static final int ERRNO_NOT_FOUND = 605;
    private static final int ERRNO_BAD_REQUEST = 402;

    private final LitemallAddressServiceLayer addressService;

    public LitemallAddressController(LitemallAddressServiceLayer addressService) {
        this.addressService = addressService;
    }

    @GetMapping("/list")
    public ApiResponse<List<AddressDtoResponse>> list(@RequestHeader("X-User-Id") Integer userId) {
        List<AddressDtoResponse> addresses = addressService.list(new LitemallUserId(userId)).stream()
                .map(AddressDtoResponse::from)
                .collect(Collectors.toList());
        return ApiResponse.ok(addresses);
    }

    @GetMapping("/detail")
    public ApiResponse<AddressDtoResponse> detail(@RequestHeader("X-User-Id") Integer userId,
                                                  @RequestParam Integer id) {
        LitemallAddressAggregate address = addressService.detail(
                new LitemallUserId(userId), new LitemallAddressId(id));
        if (address == null) {
            return ApiResponse.fail(ERRNO_NOT_FOUND, "Address not found");
        }
        return ApiResponse.ok(AddressDtoResponse.from(address));
    }

    @PostMapping("/save")
    public ApiResponse<Integer> save(@RequestHeader("X-User-Id") Integer userId,
                                     @RequestBody AddressSaveRequest request) {
        try {
            LitemallUserId owner = new LitemallUserId(userId);
            Integer savedId = addressService.save(owner, request.toAggregate(owner));
            return ApiResponse.ok(savedId);
        } catch (IllegalArgumentException e) {
            log.warn("Address save rejected for userId={}: {}", userId, e.getMessage());
            return ApiResponse.fail(ERRNO_BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestHeader("X-User-Id") Integer userId,
                                    @RequestBody AddressSaveRequest request) {
        if (request.getId() == null || request.getId() <= 0) {
            return ApiResponse.fail(ERRNO_BAD_REQUEST, "Address id is required");
        }
        try {
            addressService.delete(new LitemallUserId(userId), new LitemallAddressId(request.getId()));
            return ApiResponse.ok(null);
        } catch (IllegalArgumentException e) {
            log.warn("Address delete rejected for userId={}: {}", userId, e.getMessage());
            return ApiResponse.fail(ERRNO_NOT_FOUND, e.getMessage());
        }
    }
}
