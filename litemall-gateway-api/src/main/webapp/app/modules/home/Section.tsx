import React from 'react';
import { Link } from 'react-router-dom';

import { useTranslation } from 'app/i18n';

/**
 * One titled home zone, optionally with a "See more" link.
 *
 * Extracted from `Home.tsx` in Wave 27 so the season rail renders as a home
 * zone like every other one instead of re-implementing the markup — a season
 * is ordinary merchandising, and should not look like a special case.
 */
const Section: React.FC<{ title: string; moreTo?: string; children: React.ReactNode }> = ({ title, moreTo, children }) => {
  const { t } = useTranslation('content');
  return (
  <section className="lm-section">
    <div className="lm-section__head">
      <h2 className="lm-section__title">{title}</h2>
      {moreTo && (
        <Link to={moreTo} className="lm-section__more">
          {t('home.seeMore')}
        </Link>
      )}
    </div>
    {children}
  </section>
  );
};

export default Section;
