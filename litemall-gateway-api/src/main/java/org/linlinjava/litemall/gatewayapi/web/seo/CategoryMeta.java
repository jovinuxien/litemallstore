package org.linlinjava.litemall.gatewayapi.web.seo;

/**
 * What the category landing read ({@code GET /srv/search/category/{id}?size=1})
 * tells the head renderer: the display name and how many products currently sit
 * behind it.
 *
 * <p>{@code total} exists for one reason — the Wave-26 narrowing left twelve
 * real, non-deleted L1 departments with zero on-sale products. Those pages must
 * not 404 (the rows are intact and a restore brings them back within a nightly
 * cycle), but they are thin pages that should not be indexed while empty.
 * Absent categories are a different answer entirely and never reach this record.
 */
public record CategoryMeta(String id, String name, int total) {

    public boolean isEmpty() {
        return total <= 0;
    }
}
