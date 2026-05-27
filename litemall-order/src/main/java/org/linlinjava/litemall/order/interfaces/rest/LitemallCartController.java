package org.linlinjava.litemall.order.interfaces.rest;

import org.linlinjava.litemall.order.application.LitemallOrderOrchestratorService;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCartAggregate;
import org.linlinjava.litemall.order.domain.model.commands.cart.LitemallAddCartItemCommand;
import org.linlinjava.litemall.order.domain.model.commands.cart.LitemallUpdateCartItemCommand;
import org.linlinjava.litemall.order.domain.model.valueobjects.LitemallCartId;
import org.linlinjava.litemall.order.domain.model.valueobjects.user.LitemallUserId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Cart CRUD endpoints, used by the admin SPA to manage customers' carts.
 * Every call delegates to {@link LitemallOrderOrchestratorService}; this
 * controller never touches the cart persistence layer directly.
 */
@RestController
@RequestMapping("/srv/cart")
public class LitemallCartController {

    private final LitemallOrderOrchestratorService orchestrator;

    public LitemallCartController(LitemallOrderOrchestratorService orchestrator) {
        this.orchestrator = orchestrator;
    }

    @GetMapping
    public ResponseEntity<List<LitemallCartAggregate>> list(@RequestHeader("X-User-Id") Integer userId) {
        return ResponseEntity.ok(orchestrator.getCart(new LitemallUserId(userId)));
    }

    @GetMapping("/{cartId}")
    public ResponseEntity<LitemallCartAggregate> get(@PathVariable Integer cartId) {
        return ResponseEntity.ok(orchestrator.getCartItem(new LitemallCartId(cartId)));
    }

    @PostMapping
    public ResponseEntity<LitemallCartAggregate> add(@RequestHeader("X-User-Id") Integer userId,
                                                     @RequestBody LitemallAddCartItemCommand command) {
        command.setUserId(userId);
        return ResponseEntity.ok(orchestrator.addCartItem(command));
    }

    @PutMapping("/{cartId}")
    public ResponseEntity<LitemallCartAggregate> update(@RequestHeader("X-User-Id") Integer userId,
                                                        @PathVariable Integer cartId,
                                                        @RequestBody LitemallUpdateCartItemCommand command) {
        command.setUserId(userId);
        command.setCartId(cartId);
        return ResponseEntity.ok(orchestrator.updateCartItem(command));
    }

    @DeleteMapping("/{cartId}")
    public ResponseEntity<Void> remove(@PathVariable Integer cartId) {
        orchestrator.removeCartItem(new LitemallCartId(cartId));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clear(@RequestHeader("X-User-Id") Integer userId) {
        orchestrator.clearCart(new LitemallUserId(userId));
        return ResponseEntity.noContent().build();
    }
}
