package org.linlinjava.litemall.goods.infrastructure.acl.utils;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Builds {@link RestTemplate}s with explicit connect/read timeouts for outbound CJ calls.
 *
 * <p>The shared {@code restTemplate} bean (litemall-core {@code RestTemplateConfig}) has NO
 * timeouts — JDK default is infinite, so a wedged CJ endpoint would hang the calling thread
 * forever. New CJ clients build their own instance here instead of adding a second
 * {@code RestTemplate} bean, which would break every existing unqualified injection point
 * with a {@code NoUniqueBeanDefinitionException}.
 */
public final class CjTimedRestTemplates {

    /** Connect timeout for CJ endpoints (ms). */
    public static final int CONNECT_TIMEOUT_MS = 5000;
    /** Read timeout for CJ endpoints (ms) — CJ is slow but should answer well within this. */
    public static final int READ_TIMEOUT_MS = 15000;

    private CjTimedRestTemplates() {
    }

    public static RestTemplate withDefaultTimeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return new RestTemplate(factory);
    }
}
