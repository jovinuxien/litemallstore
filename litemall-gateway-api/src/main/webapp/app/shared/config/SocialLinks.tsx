import { useTranslation } from 'app/i18n';
import React, { useEffect, useSyncExternalStore } from 'react';

import {
  loadSiteConfig,
  siteConfigSnapshot,
  subscribeSiteConfig,
} from 'app/shared/config/siteConfig';

/**
 * Social profile icons driven by `/auth/site-config` (Wave-9.1). A network is
 * rendered ONLY when its URL is configured: pages that do not exist yet (the
 * committed default for everything but Facebook) must be hidden icons, never
 * dead links. Activation is a runtime config change — env var + restart, no
 * SPA rebuild — because the URLs arrive per page load, not at bundle time.
 */

const NETWORKS = [
  { key: 'socialFacebookUrl', icon: 'bi-facebook', labelKey: 'social.facebook' },
  { key: 'socialInstagramUrl', icon: 'bi-instagram', labelKey: 'social.instagram' },
  { key: 'socialTiktokUrl', icon: 'bi-tiktok', labelKey: 'social.tiktok' },
  { key: 'socialXUrl', icon: 'bi-twitter-x', labelKey: 'social.x' },
  { key: 'socialYoutubeUrl', icon: 'bi-youtube', labelKey: 'social.youtube' },
] as const;

const SocialLinks: React.FC<{ className?: string; linkClassName?: string }> = ({
  className = 'd-flex gap-3 fs-5',
  linkClassName = 'link-light',
}) => {
  const { t } = useTranslation();
  const { config } = useSyncExternalStore(subscribeSiteConfig, siteConfigSnapshot);

  useEffect(() => {
    // Shared, deduped fetch — a no-op when MatomoTracker already loaded it.
    loadSiteConfig();
  }, []);

  const links = NETWORKS.filter(n => config[n.key]);
  if (links.length === 0) return null;

  return (
    <div className={className}>
      {links.map(n => (
        <a
          key={n.key}
          href={config[n.key] as string}
          target='_blank'
          rel='noopener noreferrer'
          aria-label={t(n.labelKey)}
          className={linkClassName}
        >
          <i className={`bi ${n.icon}`} aria-hidden='true' />
        </a>
      ))}
    </div>
  );
};

export default SocialLinks;
