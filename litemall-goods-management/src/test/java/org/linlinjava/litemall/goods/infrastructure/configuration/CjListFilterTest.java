package org.linlinjava.litemall.goods.infrastructure.configuration;

import org.junit.jupiter.api.Test;
import org.linlinjava.litemall.goods.infrastructure.acl.cache.CjRawCacheRepository;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 26 Phase 2 deliverable 4: the sourcing filter enforces CJ's measured constraints locally, so
 * a misconfiguration fails fast with a readable message instead of as an opaque upstream 400.
 */
public class CjListFilterTest {

    @Test
    public void rejectsCommaSeparatedCountryList() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new CjListFilter("DE,FR", null, null));
        assertTrue(ex.getMessage().contains("ONE code"),
                "the message must explain WHY, since CJ's own error ('only within 4 characters') "
                        + "misleads you into thinking a shorter list would work");
    }

    @Test
    public void rejectsCountryCodeOverFourCharacters() {
        assertThrows(IllegalArgumentException.class, () -> new CjListFilter("GERMANY", null, null));
    }

    @Test
    public void rejectsInvertedPriceBand() {
        assertThrows(IllegalArgumentException.class,
                () -> new CjListFilter("DE", new BigDecimal("32"), new BigDecimal("10")));
    }

    @Test
    public void normalizesBlankAndWhitespaceToAbsent() {
        assertNull(new CjListFilter("   ", null, null).countryCode());
        assertNull(new CjListFilter("", null, null).countryCode());
        assertEquals("DE", new CjListFilter("  DE ", null, null).countryCode());
    }

    @Test
    public void noneIsEmptyAndContributesNoCacheSuffix() {
        assertTrue(CjListFilter.NONE.isEmpty());
        assertEquals("", CjListFilter.NONE.cacheSuffix());
        assertEquals(CjRawCacheRepository.listKey("LEAF", 1),
                CjRawCacheRepository.listKey("LEAF", 1, CjListFilter.NONE.cacheSuffix()),
                "an unfiltered fetch must use the SAME cache key as before Wave 26");
    }

    @Test
    public void filteredFetchCannotReuseAnUnfilteredCachedPage() {
        CjListFilter de = new CjListFilter("DE", new BigDecimal("10"), new BigDecimal("32"));
        String unfiltered = CjRawCacheRepository.listKey("LEAF", 1, CjListFilter.NONE.cacheSuffix());
        String filtered = CjRawCacheRepository.listKey("LEAF", 1, de.cacheSuffix());
        assertNotEquals(unfiltered, filtered,
                "sharing a key would silently serve the unfiltered product set and look exactly "
                        + "as if the filter had done nothing");
    }

    @Test
    public void differentFiltersDoNotCollide() {
        String de = new CjListFilter("DE", null, null).cacheSuffix();
        String us = new CjListFilter("US", null, null).cacheSuffix();
        String deBanded = new CjListFilter("DE", new BigDecimal("10"), new BigDecimal("32")).cacheSuffix();
        assertNotEquals(de, us);
        assertNotEquals(de, deBanded);
    }
}
