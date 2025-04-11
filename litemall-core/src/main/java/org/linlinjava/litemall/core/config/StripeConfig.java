package org.linlinjava.litemall.core.config;


import com.stripe.Stripe;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import javax.annotation.PostConstruct;

@Configuration
public class StripeConfig {

    @Autowired
    private StripeProperties stripeProperties;

    @PostConstruct
    public void initStripe(){
        Stripe.apiKey = stripeProperties.getSecretKey();
    }


}
