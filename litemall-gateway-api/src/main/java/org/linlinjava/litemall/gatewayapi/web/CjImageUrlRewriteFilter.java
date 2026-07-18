package org.linlinjava.litemall.gatewayapi.web;

import org.reactivestreams.Publisher;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Rewrites CJ Dropshipping CDN image URLs to same-origin proxy paths in JSON
 * response bodies, so product images render in browsers that cannot reach
 * {@code cf.cjdropshipping.com} directly (ad-blockers treat it as a third-party
 * tracker; some networks/regions block it outright).
 *
 * <p>The edge (Caddy) proxies {@code /_cdn/cf/*} → {@code https://cf.cjdropshipping.com}
 * and {@code /_cdn/oss/*} → {@code https://oss-cf.cjdropshipping.com}. This filter is
 * the single chokepoint that makes EVERY response use those paths — including the ones
 * a DB rewrite cannot reach, namely the live CJ search/catalog calls
 * ({@code ?source=cj}) whose image URLs come straight from CJ's API and never touch
 * our database. Doing it here, once, covers DB-served and live-CJ paths uniformly.
 *
 * <p><b>Scope guards</b> (this buffers the response body, so it must stay cheap and
 * safe):
 * <ul>
 *   <li>Only {@code application/json} responses are touched — never images, HTML, or
 *       SSE ({@code text/event-stream}).</li>
 *   <li>Compressed responses ({@code Content-Encoding} present) are passed through
 *       untouched — a byte-level string replace would corrupt a gzip stream.
 *       Internal service→gateway hops are uncompressed; Caddy compresses AFTER us.</li>
 *   <li>{@code Content-Length} is recomputed after the substring swap (the proxy path
 *       is shorter than the absolute URL), so no truncation.</li>
 * </ul>
 *
 * <p>Runs after routing so it decorates the real upstream response; the decorator's
 * {@code writeWith} does the transform when the body is written back to the client.
 */
@Component
public class CjImageUrlRewriteFilter implements GlobalFilter, Ordered {

    // Relative targets, deliberately: they resolve against whatever host serves the
    // SPA (shop.localhost now, the real domain later) with no hard-coded origin.
    private static final String[][] SWAPS = {
        {"https://cf.cjdropshipping.com/", "/_cdn/cf/"},
        {"http://cf.cjdropshipping.com/",  "/_cdn/cf/"},
        {"https://oss-cf.cjdropshipping.com/", "/_cdn/oss/"},
        {"http://oss-cf.cjdropshipping.com/",  "/_cdn/oss/"},
    };

    // A hair before NettyWriteResponseFilter (-1) so our decorated response is the one
    // that gets written.
    private static final int ORDER = -2;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Force downstream services to answer UNCOMPRESSED so the body below is
        // rewritable text, not a gzip stream. Without this the whole filter is a
        // no-op for real browsers: they (and Caddy) send Accept-Encoding: gzip,
        // the service returns Content-Encoding: gzip, and rewritable() skips it —
        // which is exactly why the first cut appeared to do nothing through the
        // edge while working on a plain localhost curl. Caddy re-compresses on the
        // way to the client, so nothing is lost on the wire.
        exchange = exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .headers(h -> h.remove(HttpHeaders.ACCEPT_ENCODING))
                        .build())
                .build();

        ServerHttpResponse original = exchange.getResponse();
        DataBufferFactory bufferFactory = original.bufferFactory();

        ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(original) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                HttpHeaders headers = getDelegate().getHeaders();
                if (!rewritable(headers)) {
                    return super.writeWith(body);
                }
                // Join the whole (bounded JSON) body, transform, rewrite length.
                return super.writeWith(DataBufferUtils.join(body).map(buffer -> {
                    byte[] in = new byte[buffer.readableByteCount()];
                    buffer.read(in);
                    DataBufferUtils.release(buffer);
                    String s = new String(in, StandardCharsets.UTF_8);
                    if (s.indexOf("cjdropshipping.com") >= 0) {   // avoid churn when absent
                        for (String[] swap : SWAPS) {
                            s = s.replace(swap[0], swap[1]);
                        }
                    }
                    byte[] out = s.getBytes(StandardCharsets.UTF_8);
                    getDelegate().getHeaders().setContentLength(out.length);
                    return bufferFactory.wrap(out);
                }));
            }

            @Override
            public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
                // Streaming path (e.g. SSE) — never buffer/rewrite these.
                return super.writeAndFlushWith(body);
            }
        };

        return chain.filter(exchange.mutate().response(decorated).build());
    }

    private static boolean rewritable(HttpHeaders headers) {
        if (headers.containsKey(HttpHeaders.CONTENT_ENCODING)) {
            return false; // don't touch gzip/deflate/br
        }
        MediaType ct = headers.getContentType();
        return ct != null
                && (MediaType.APPLICATION_JSON.includes(ct)
                    || "json".equalsIgnoreCase(ct.getSubtype()));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
