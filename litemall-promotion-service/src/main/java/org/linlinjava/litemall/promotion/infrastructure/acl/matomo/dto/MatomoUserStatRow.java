package org.linlinjava.litemall.promotion.infrastructure.acl.matomo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Wire shape of one row from the Matomo Reporting API "by user" report — the
 * <b>agreed contract</b> the promotion service codes against (a per-user report
 * keyed by the litemall user id via Matomo's User ID feature / a custom
 * dimension, joined with goal revenue). See
 * {@code docs/phase3-marketing-stack-integration.md} for the exact Matomo method
 * and field mapping. {@code @JsonIgnoreProperties} keeps us tolerant of the many
 * extra columns Matomo returns.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MatomoUserStatRow {

    /** The row label — Matomo carries the litemall user id here (User ID feature). */
    @JsonProperty("label")
    private String label;

    /** Engagement: number of visits in the window. */
    @JsonProperty("nb_visits")
    private Integer nbVisits;

    /** Conversions in the window — mapped to the frequency (order-count) input. */
    @JsonProperty("nb_conversions")
    private Integer nbConversions;

    /** Goal revenue in the window — mapped to the monetary (spend) input. */
    @JsonProperty("revenue")
    private BigDecimal revenue;

    /** Epoch seconds of the customer's most recent action — the recency input. */
    @JsonProperty("lastActionTimestamp")
    private Long lastActionTimestamp;
}
