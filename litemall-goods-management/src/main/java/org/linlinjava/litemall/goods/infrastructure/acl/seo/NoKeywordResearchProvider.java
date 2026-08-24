package org.linlinjava.litemall.goods.infrastructure.acl.seo;

import org.linlinjava.litemall.goods.application.seo.KeywordResearchProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@code source=none}: keyword research switched off entirely.
 *
 * <p>Exists so "off" is a bean rather than an absent bean. Without it, turning research off
 * would fail the context at startup for want of a {@link KeywordResearchProvider} to inject —
 * which turns a configuration choice into an outage, and pushes every consumer into wrapping
 * its dependency in {@code Optional} to compensate.
 *
 * <p>An empty list is already the port's contract for "nothing available", so consumers need
 * no branch for this case.
 */
@Component
@ConditionalOnProperty(prefix = "litemall.seo-research", name = "source", havingValue = "none")
public class NoKeywordResearchProvider implements KeywordResearchProvider {

    @Override
    public List<KeywordDemand> demandFor(String seed, int limit) {
        return List.of();
    }
}
