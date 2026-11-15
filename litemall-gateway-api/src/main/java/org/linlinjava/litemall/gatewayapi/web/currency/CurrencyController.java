package org.linlinjava.litemall.gatewayapi.web.currency;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.linlinjava.litemall.gatewayapi.web.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /auth/currency} — what the storefront needs to SHOW prices in the
 * visitor's own currency while the store keeps charging EUR (display currency,
 * stage A; spec {@code docs/spec-display-currency.md}).
 *
 * <p>Answers {@code {country, suggested, enabled[], rates{}, asOf}}:
 * <ul>
 * <li>{@code enabled} — the operator's list ({@code litemall.currency.display},
 * env {@code LITEMALL_CURRENCY_DISPLAY}, default {@code EUR} = feature off)
 * narrowed to currencies we support AND hold a fresh ECB rate for. EUR is
 * always first. One entry ⇒ the SPA renders no selector and converts nothing.
 * <li>{@code suggested} — the visitor's country (Cloudflare's
 * {@code CF-IPCountry}) mapped to its currency when that currency is enabled,
 * else EUR. No header (dev, direct hits) ⇒ EUR.
 * <li>{@code rates} — units per one euro, ECB reference rate fixed {@code asOf}.
 * </ul>
 *
 * <p>The answer depends on who is asking, so it is {@code no-store}: a shared
 * cache must never hand a Swedish visitor's answer to a German one.
 */
@RestController
@RequestMapping("/auth")
public class CurrencyController {

    static final String BASE = "EUR";
    /** Display currencies the storefront knows how to write and round. */
    static final Set<String> SUPPORTED = Set.of(BASE, "SEK", "DKK", "NOK", "GBP");
    /** Countries whose own currency we can show. Everyone else sees the euro price. */
    static final Map<String, String> COUNTRY_CURRENCY = Map.of("SE", "SEK", "DK", "DKK", "NO", "NOK", "GB", "GBP");

    private final List<String> configured;
    private final EcbRateService rateService;

    public CurrencyController(@Value("${litemall.currency.display:EUR}") String display, EcbRateService rateService) {
        this.configured = parseCurrencies(display);
        this.rateService = rateService;
    }

    @GetMapping("/currency")
    public ResponseEntity<Map<String, Object>> currency(
            @RequestHeader(value = "CF-IPCountry", required = false) String cfCountry) {
        EcbRates ecb = configured.size() > 1 ? rateService.current() : null;

        List<String> enabled = new ArrayList<>();
        Map<String, BigDecimal> rates = new LinkedHashMap<>();
        enabled.add(BASE);
        if (ecb != null) {
            for (String code : configured) {
                BigDecimal rate = BASE.equals(code) ? null : ecb.rate(code);
                if (rate != null) {
                    enabled.add(code);
                    rates.put(code, rate);
                }
            }
        }

        String country = normalizeCountry(cfCountry);
        String mapped = country == null ? null : COUNTRY_CURRENCY.get(country);
        String suggested = mapped != null && enabled.contains(mapped) ? mapped : BASE;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("country", country);
        data.put("suggested", suggested);
        data.put("enabled", enabled);
        data.put("rates", rates);
        data.put("asOf", ecb == null ? null : ecb.asOf().toString());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(data));
    }

    /** Two ASCII letters, upper-cased; Cloudflare's "XX" (unknown) and "T1" (Tor) are no country. */
    static String normalizeCountry(String raw) {
        if (raw == null) {
            return null;
        }
        String c = raw.trim().toUpperCase(Locale.ROOT);
        if (!c.matches("[A-Z]{2}") || "XX".equals(c)) {
            return null;
        }
        return c;
    }

    /**
     * "eur, sek,DKK,xyz" → [EUR, SEK, DKK]: EUR always first, unsupported codes
     * dropped (a typo hides one currency, it never breaks boot), no duplicates.
     */
    static List<String> parseCurrencies(String csv) {
        List<String> out = new ArrayList<>();
        out.add(BASE);
        if (csv != null) {
            for (String raw : csv.split(",")) {
                String code = raw.trim().toUpperCase(Locale.ROOT);
                if (SUPPORTED.contains(code) && !out.contains(code)) {
                    out.add(code);
                }
            }
        }
        return out;
    }
}
