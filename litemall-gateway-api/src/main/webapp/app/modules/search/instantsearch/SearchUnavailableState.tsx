import React from 'react';
import { Link } from 'react-router-dom';

import { useTranslation } from 'app/i18n';

/**
 * Honest search-outage state (Wave-19). Shown by /search instead of the
 * generic "no results" empty state when the backend reported its typed
 * errno 502 ("Search is temporarily unavailable" — OCS searcher down) or the
 * gateway was unreachable; `litemallSearchClient` publishes the signal as
 * `search.meta.unavailable`. Zero results here are an OUTAGE, not a relevance
 * miss, so no trending/category suggestions — just the truth and a way back.
 */
const SearchUnavailableState: React.FC = () => {
  const { t } = useTranslation('search');
  return (
    <div className="lm-isearch__empty" role="alert">
      <h2>{t('unavailable.title')}</h2>
      <p>{t('unavailable.body')}</p>
      <div className="lm-isearch__empty-chips">
        <button type="button" className="lm-isearch__empty-chip" onClick={() => window.location.reload()}>
          {t('unavailable.retry')}
        </button>
        <Link to="/" className="lm-isearch__empty-chip lm-isearch__empty-chip--cat">
          {t('unavailable.home')}
        </Link>
      </div>
    </div>
  );
};

export default SearchUnavailableState;
