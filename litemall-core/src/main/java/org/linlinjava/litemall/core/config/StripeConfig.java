package org.linlinjava.litemall.core.config;


import com.stripe.Stripe;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {

    @Autowired
    private StripeProperties stripeProperties;

    @PostConstruct
    public void initStripe(){
        Stripe.apiKey = stripeProperties.getSecretKey();
    }


}
