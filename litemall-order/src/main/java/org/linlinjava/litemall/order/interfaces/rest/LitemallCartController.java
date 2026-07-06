package org.linlinjava.litemall.order.interfaces.rest;

import org.linlinjava.litemall.order.application.LitemallOrderOrchestratorService;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCartAggregate;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallCartId;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallMoney;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsId;
import org.linlinjava.litemall.order.domain.model.valueobjects.goods.LitemallGoodsProductId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.linlinjava.litemall.order.domain.model.valueobjects.ApiResponse;
import org.linlinjava.litemall.order.interfaces.dtos.cart.AddCartItemRequest;
import org.linlinjava.litemall.order.interfaces.dtos.cart.LegacyAddToCartRequest;
import org.linlinjava.litemall.order.interfaces.dtos.cart.LegacyCartIndexDto;
import org.linlinjava.litemall.order.interfaces.dtos.cart.LegacyCartItemDto;
import org.linlinjava.litemall.order.interfaces.dtos.cart.UpdateCartItemRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/srv/cart")
public class LitemallCartController {

    private final LitemallOrderOrchestratorService orchestrator;

    public LitemallCartController(LitemallOrderOrchestratorService orchestrator) {
        this.orchestrator = orchestrator;
    }

    @GetMapping("/items")
    public List<LitemallCartAggregate> list(@RequestParam Integer userId) {
        return orchestrator.listCartItems(new LitemallUserId(userId));
    }

    @GetMapping("/items/{cartItemId}")
    public LitemallCartAggregate get(@PathVariable Integer cartItemId,
                                     @RequestParam Integer userId) {
        return orchestrator.getCartItem(new LitemallCartId(cartItemId), new LitemallUserId(userId));
    }

    @PostMapping("/items")
    public ResponseEntity<LitemallCartAggregate> add(@RequestBody AddCartItemRequest req) {
        LitemallCartAggregate cart = toAggregate(req);
        LitemallCartAggregate saved = orchestrator.addCartItem(cart);
        return ResponseEntity.status(201).body(saved);
    }

    @PutMapping("/items/{cartItemId}")
    public LitemallCartAggregate update(@PathVariable Integer cartItemId,
                                        @RequestParam Integer userId,
                                        @RequestBody UpdateCartItemRequest req) {
        return orchestrator.updateCartItem(
                new LitemallCartId(cartItemId),
                new LitemallUserId(userId),
                req.getNumber(),
                req.getSpecifications());
    }

    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<Void> remove(@PathVariable Integer cartItemId,
                                       @RequestParam Integer userId) {
        orchestrator.removeCartItem(new LitemallCartId(cartItemId), new LitemallUserId(userId));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/items")
    public ResponseEntity<Void> clear(@RequestParam Integer userId) {
        orchestrator.clearCart(new LitemallUserId(userId));
        return ResponseEntity.noContent().build();
    }

    // =====================================================================
    // Legacy customer-SPA cart contract (cartSlice.ts): flat {errno,errmsg,data}
    // envelopes, userId from the trusted gateway header (never the body). These
    // back the everyday cart page that the SPA hits at /srv/cart/index|add|update;
    // the RESTful /items endpoints above remain the canonical CRUD surface.
    // =====================================================================

    /** GET /srv/cart/index → the user's active cart lines + totals (SPA cart page). */
    @GetMapping("/index")
    public ApiResponse<LegacyCartIndexDto> index(@RequestHeader("X-User-Id") Integer userId) {
        return ok(LegacyCartIndexDto.from(orchestrator.listCartItems(new LitemallUserId(userId))));
    }

    /** POST /srv/cart/add {goodsId,productId,number} → enrich + add a checked line; returns refreshed cart. */
    @PostMapping("/add")
    public ApiResponse<LegacyCartIndexDto> addLegacy(@RequestHeader("X-User-Id") Integer userId,
                                                     @RequestBody LegacyAddToCartRequest req) {
        LitemallUserId uid = new LitemallUserId(userId);
        orchestrator.addToCart(uid, req.getGoodsId(), req.getProductId(), req.getNumber());
        return ok(LegacyCartIndexDto.from(orchestrator.listCartItems(uid)));
    }

    /** PUT /srv/cart/update {id,number,specifications,...} → update a line; returns the updated line. */
    @PutMapping("/update")
    public ApiResponse<LegacyCartItemDto> updateLegacy(@RequestHeader("X-User-Id") Integer userId,
                                                       @RequestBody LegacyCartItemDto req) {
        LitemallCartAggregate updated = orchestrator.updateCartItem(
                new LitemallCartId(req.getId()), new LitemallUserId(userId),
                req.getNumber(), req.getSpecifications());
        return ok(LegacyCartItemDto.from(updated));
    }

    private static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setErrno(0);
        r.setErrmsg("");
        r.setData(data);
        return r;
    }

    private static LitemallCartAggregate toAggregate(AddCartItemRequest req) {
        LitemallCartAggregate cart = new LitemallCartAggregate();
        cart.setUserId(new LitemallUserId(req.getUserId()));
        cart.setGoodsId(new LitemallGoodsId(req.getGoodsId()));
        cart.setProductId(new LitemallGoodsProductId(req.getProductId()));
        cart.setNumber(req.getNumber() != null ? req.getNumber() : 1);
        cart.setSpecifications(req.getSpecifications());
        cart.setGoodsSn(req.getGoodsSn());
        cart.setGoodsName(req.getGoodsName());
        if (req.getPrice() != null) {
            cart.setPrice(new LitemallMoney(req.getPrice()));
        }
        cart.setPicUrl(req.getPicUrl());
        cart.setChecked(true);
        return cart;
    }
}
