package org.linlinjava.litemall.promotion.domain.service.targeting;

import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.AudienceMember;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.CustomerStatistics;
import org.linlinjava.litemall.promotion.domain.model.valueobjects.targeting.TargetingCriteria;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Audience-selection port (Katsov §3.5 targeting). Given a campaign's
 * {@link TargetingCriteria} and a customer-statistics population, returns the
 * matching {@link AudienceMember}s. The default implementation is rules + RFM
 * statistics; a later ML phase (propensity / uplift / LTV) can provide an
 * alternative implementation behind this same interface without reworking
 * callers — that is the seam the Phase-2 vision calls for.
 */
public interface AudienceSelector {

    List<AudienceMember> select(TargetingCriteria criteria,
                                List<CustomerStatistics> population,
                                LocalDateTime now);
}
