package org.linlinjava.litemall.gatewayapi.web.seo;

import reactor.core.publisher.Mono;

/**
 * What the fallback filter needs from the meta layer, kept as an interface so
 * the filter's tests can stub it without a mocking framework (the inherited
 * test classpath carries a Mockito far too old to mock classes on JDK 21).
 * {@link SeoMetaClient} is the production implementation.
 *
 * <p>No method ever completes with an error, and none completes empty: every
 * one answers a {@link MetaLookup}, which says whether the row was found, was
 * reported missing in contract, or could not be looked up at all. That
 * distinction is the whole point — see {@link MetaLookup}.
 */
public interface SeoMetaSource {

    Mono<MetaLookup<GoodsMeta>> goodsMeta(String goodsId);

    Mono<MetaLookup<CategoryMeta>> category(String categoryId);

    Mono<MetaLookup<PageMeta>> pageMeta(String pageId);

    /** The active season page, or absent when no season is running. */
    Mono<MetaLookup<PageMeta>> seasonPage();
}
