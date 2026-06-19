package org.linlinjava.litemall.order.interfaces.dtos.cart;

import lombok.Data;
import org.linlinjava.litemall.order.domain.model.agregates.LitemallCartAggregate;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response of the legacy customer-SPA cart page ({@code GET /srv/cart/index}):
 * the user's active cart lines plus aggregate totals, matching the SPA's
 * {@code {cartList, cartTotal}} shape.
 */
@Data
public class LegacyCartIndexDto {

    private List<LegacyCartItemDto> cartList;
    private CartTotal cartTotal;

    @Data
    public static class CartTotal {
        private int goodsCount;
        private int checkedGoodsCount;
        private BigDecimal goodsAmount = BigDecimal.ZERO;
        private BigDecimal checkedGoodsAmount = BigDecimal.ZERO;
    }

    public static LegacyCartIndexDto from(List<LitemallCartAggregate> items) {
        LegacyCartIndexDto dto = new LegacyCartIndexDto();
        CartTotal total = new CartTotal();
        dto.setCartList(items.stream().map(LegacyCartItemDto::from).toList());
        for (LitemallCartAggregate c : items) {
            int number = c.getNumber() == null ? 0 : c.getNumber();
            BigDecimal price = c.getPrice() == null ? BigDecimal.ZERO : c.getPrice().getAmount();
            BigDecimal lineAmount = price.multiply(BigDecimal.valueOf(number));
            total.setGoodsCount(total.getGoodsCount() + number);
            total.setGoodsAmount(total.getGoodsAmount().add(lineAmount));
            if (c.isChecked()) {
                total.setCheckedGoodsCount(total.getCheckedGoodsCount() + number);
                total.setCheckedGoodsAmount(total.getCheckedGoodsAmount().add(lineAmount));
            }
        }
        dto.setCartTotal(total);
        return dto;
    }
}
