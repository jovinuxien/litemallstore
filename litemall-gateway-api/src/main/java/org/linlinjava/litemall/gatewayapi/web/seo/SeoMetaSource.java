package org.linlinjava.litemall.gatewayapi.web.seo;

import reactor.core.publisher.Mono;

/**
 * What the fallback filter needs from the meta layer, kept as an interface so
 * the filter's tests can stub it without a mocking framework (the inherited
 * test classpath carries a Mockito far too old to mock classes on JDK 21).
 * {@link SeoMetaClient} is the production implementation.
 *
 * <p>Both methods complete EMPTY — never with an error — when the answer
 * cannot be had within budget; callers treat empty as "serve the plain shell".
 */
public interface SeoMetaSource {

    Mono<GoodsMeta> goodsMeta(String goodsId);

    Mono<String> categoryName(String categoryId);
}
