package org.linlinjava.litemall.order.application.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingLinkBuilderTest {

    @Test
    void blankTemplate_meansTheDefault_becauseComposePassesAnUnsetVariableAsEmpty() {
        assertThat(new TrackingLinkBuilder("").urlFor("EQK1")).isEqualTo("https://t.17track.net/en#nums=EQK1");
        assertThat(new TrackingLinkBuilder(null).urlFor("EQK1")).isEqualTo("https://t.17track.net/en#nums=EQK1");
    }

    @Test
    void aTemplateWithoutThePlaceholder_orABlankNumber_yieldsNoLink() {
        assertThat(new TrackingLinkBuilder("none").urlFor("EQK1")).isNull();
        assertThat(new TrackingLinkBuilder(TrackingLinkBuilder.DEFAULT_TEMPLATE).urlFor(" ")).isNull();
    }

    @Test
    void aCustomTemplate_isFilledWithTheEncodedNumber() {
        assertThat(new TrackingLinkBuilder("https://www.cjpacket.com/?trackingNumber={trackNumber}").urlFor("EQ K1"))
                .isEqualTo("https://www.cjpacket.com/?trackingNumber=EQ+K1");
    }
}
