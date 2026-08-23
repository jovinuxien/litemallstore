package org.linlinjava.litemall.gatewayapi.web.seo;

/**
 * The three answers a meta lookup can give, kept apart because two of them
 * used to be one.
 *
 * <p>Before this type, {@link SeoMetaSource} answered {@code Mono.empty()} both
 * when goods-management said "no such product" and when goods-management could
 * not be reached at all. The fallback filter therefore had to treat every
 * unknown as "serve the plain shell, 200" — which is right for an outage and
 * wrong for a genuinely missing URL, and is why {@code /product/99999999}
 * answered 200 with the generic shell (a soft 404 in Search Console).
 *
 * <p>Telling them apart is what lets the filter return a real 404 for
 * {@link #absent()} while keeping {@link #unavailable()} strictly fail-open. The
 * asymmetry is deliberate and load-bearing: mislabelling an outage as "absent"
 * would 404 the entire catalogue for as long as goods-management is down, and
 * Google would drop every product page it managed to crawl in that window.
 * Only an explicit non-zero errno envelope — the service answering, in
 * contract, that the row does not exist — counts as absent. A timeout, a
 * connection failure, a 5xx, an unparseable body or a payload whose shape we no
 * longer recognise all stay unavailable.
 */
public record MetaLookup<T>(T value, boolean absent) {

    private static final MetaLookup<?> ABSENT = new MetaLookup<>(null, true);
    private static final MetaLookup<?> UNAVAILABLE = new MetaLookup<>(null, false);

    /** The service answered with a usable payload. */
    public static <T> MetaLookup<T> found(T value) {
        return new MetaLookup<>(value, false);
    }

    /**
     * The service answered, in contract, that there is no such row. Named
     * {@code missing} because {@code absent} is the record component's own
     * accessor — the state is read as {@code lookup.absent()}.
     */
    @SuppressWarnings("unchecked")
    public static <T> MetaLookup<T> missing() {
        return (MetaLookup<T>) ABSENT;
    }

    /** The answer could not be had — treat exactly as before this type existed. */
    @SuppressWarnings("unchecked")
    public static <T> MetaLookup<T> unavailable() {
        return (MetaLookup<T>) UNAVAILABLE;
    }

    public boolean isFound() {
        return value != null;
    }
}
