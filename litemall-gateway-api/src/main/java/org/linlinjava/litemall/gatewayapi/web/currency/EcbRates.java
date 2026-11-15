package org.linlinjava.litemall.gatewayapi.web.currency;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * One day's euro reference rates as the European Central Bank publishes them
 * ({@code eurofxref-daily.xml}): units of each currency per ONE euro, plus the
 * date the ECB fixed them. Immutable.
 *
 * <p>The file nests three {@code Cube} levels — an outer wrapper, one carrying
 * {@code time='yyyy-MM-dd'}, and one per currency carrying {@code currency} +
 * {@code rate}. The parser reads attributes only and ignores nesting, so it
 * survives namespace-prefix changes; anything it cannot read as a positive
 * decimal is dropped rather than guessed.
 */
public final class EcbRates {

    private final LocalDate asOf;
    private final Map<String, BigDecimal> perEuro;

    EcbRates(LocalDate asOf, Map<String, BigDecimal> perEuro) {
        this.asOf = asOf;
        this.perEuro = Collections.unmodifiableMap(new LinkedHashMap<>(perEuro));
    }

    public LocalDate asOf() {
        return asOf;
    }

    /** Units of {@code currency} per one euro, or null when the ECB did not list it. */
    public BigDecimal rate(String currency) {
        return perEuro.get(currency);
    }

    /**
     * Parses the ECB daily file. Throws {@link IllegalArgumentException} when the
     * document has no dated cube or no usable rate — a half-read file must never
     * become a price.
     */
    public static EcbRates parse(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("empty ECB document");
        }
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            // Remote XML: no DTDs, no external entities.
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            f.setNamespaceAware(true);
            javax.xml.parsers.DocumentBuilder builder = f.newDocumentBuilder();
            builder.setErrorHandler(null); // failures surface as the exception below, not on stderr
            NodeList cubes = builder
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                    .getElementsByTagNameNS("*", "Cube");
            LocalDate asOf = null;
            Map<String, BigDecimal> rates = new LinkedHashMap<>();
            for (int i = 0; i < cubes.getLength(); i++) {
                Element e = (Element) cubes.item(i);
                if (e.hasAttribute("time") && asOf == null) {
                    asOf = LocalDate.parse(e.getAttribute("time").trim());
                }
                if (e.hasAttribute("currency") && e.hasAttribute("rate")) {
                    try {
                        BigDecimal rate = new BigDecimal(e.getAttribute("rate").trim());
                        if (rate.signum() > 0) {
                            rates.put(e.getAttribute("currency").trim().toUpperCase(java.util.Locale.ROOT), rate);
                        }
                    } catch (NumberFormatException ignored) {
                        // one unreadable currency does not poison the others
                    }
                }
            }
            if (asOf == null || rates.isEmpty()) {
                throw new IllegalArgumentException("ECB document carries no dated rates");
            }
            return new EcbRates(asOf, rates);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("unreadable ECB document: " + e.getMessage(), e);
        }
    }
}
