package org.linlinjava.litemall.wx.web;

import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.linlinjava.litemall.core.util.ResponseUtil;
import org.linlinjava.litemall.db.domain.LitemallOrder;
import org.linlinjava.litemall.db.service.LitemallOrderService;
import org.linlinjava.litemall.wx.annotation.LoginUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/wx/stripe")
public class WxStripeController {

    private final Log logger = LogFactory.getLog(WxStripeController.class);

    @Autowired
    private LitemallOrderService orderService;

    @Value("${litemall.stripe.secret-key}")
    private String stripeSecretKey;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeSecretKey;
    }

    @PostMapping("/create-payment-intent")
    public Object createPaymentIntent(@LoginUser Integer userId, @RequestBody Map<String, Object> paymentData) throws Exception {
        if (userId == null) {
            return ResponseUtil.unlogin();
        }

        String orderId = (String) paymentData.get("orderId");
        LitemallOrder order = orderService.findById(Integer.parseInt(orderId));

        if (order == null || !order.getUserId().equals(userId)) {
            return ResponseUtil.badArgument();
        }
        Map<String, String> metadata = new HashMap<>();
        metadata.put("order_id", orderId);
        metadata.put("user_id", userId.toString());

        long amount = order.getActualPrice().longValue() * 100; // Convert to cents

        PaymentIntentCreateParams params =
                PaymentIntentCreateParams.builder()
                        .setAmount(amount)
                        .setCurrency("usd")
                        .setPaymentMethod((String) paymentData.get("paymentMethodId"))
                        .setConfirmationMethod(PaymentIntentCreateParams.ConfirmationMethod.MANUAL)
                        .setConfirm(true)
                        .putAllMetadata(metadata)
                        .build();

        PaymentIntent paymentIntent = PaymentIntent.create(params);

        Map<String, Object> data = new HashMap<>();
        data.put("clientSecret", paymentIntent.getClientSecret());
        data.put("paymentIntentId", paymentIntent.getId());
        data.put("status", paymentIntent.getStatus());

        return ResponseUtil.ok(data);
    }
}