package org.linlinjava.litemall.gatewayapi.web.currency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The two beans as Spring builds them — the {@code @Value} placeholders with their
 * defaults and the constructor Spring must pick on {@link EcbRateService}. Unit
 * tests that call the constructors by hand cannot catch a context that fails to boot.
 */
class CurrencyWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(EcbRateService.class, CurrencyController.class);

    @Test
    @SuppressWarnings("unchecked")
    void bootsWithNoConfigurationAtAllAndIsOff() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            Map<String, Object> data = (Map<String, Object>) ctx.getBean(CurrencyController.class)
                    .currency("SE").getBody().get("data");
            assertThat(data.get("enabled")).isEqualTo(List.of("EUR"));
            assertThat(data.get("suggested")).isEqualTo("EUR");
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void bootsEnabledEvenWhenTheEcbIsUnreachableAndStaysOnEuros() {
        runner.withPropertyValues(
                        "litemall.currency.display=EUR,SEK,DKK,NOK,GBP",
                        // nothing listens here: the fetch fails fast and must not fail the context
                        "litemall.currency.ecb-url=http://127.0.0.1:9/eurofxref-daily.xml")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    Map<String, Object> data = (Map<String, Object>) ctx.getBean(CurrencyController.class)
                            .currency("SE").getBody().get("data");
                    assertThat(data.get("enabled")).isEqualTo(List.of("EUR"));
                    assertThat(data.get("suggested")).isEqualTo("EUR");
                });
    }
}
