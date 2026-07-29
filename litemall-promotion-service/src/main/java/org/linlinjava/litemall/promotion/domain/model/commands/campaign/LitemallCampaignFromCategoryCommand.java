package org.linlinjava.litemall.promotion.domain.model.commands.campaign;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin command for the Wave-12 category campaign composer: one call creates a
 * scheduled (DRAFT) campaign targeting the category's live-deal goods plus
 * unpublished per-platform social drafts; the campaign schedule tick activates
 * it and fires the drafts when {@code schedule.start} arrives. Primitive types
 * here; the application service resolves and validates them.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LitemallCampaignFromCategoryCommand {

    /** L1 category root whose live-deal goods form the target set. */
    private Integer categoryL1Id;
    /** Optional campaign name; defaults to the category's display name. */
    private String name;
    private Schedule schedule;
    /** Platform db values ({@code meta_fb|meta_ig|tiktok}). */
    private List<String> platforms;
    /** Explicit fallback target set, used only when the category has no live-deal goods. */
    private List<Integer> goodsIds;

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Schedule {
        private LocalDateTime start;
        private LocalDateTime stop;
    }
}
