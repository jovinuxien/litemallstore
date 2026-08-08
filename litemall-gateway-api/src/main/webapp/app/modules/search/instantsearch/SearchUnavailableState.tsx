import React from 'react';
import { Link } from 'react-router-dom';

/**
 * Honest search-outage state (Wave-19). Shown by /search instead of the
 * generic "no results" empty state when the backend reported its typed
 * errno 502 ("Search is temporarily unavailable" — OCS searcher down) or the
 * gateway was unreachable; `litemallSearchClient` publishes the signal as
 * `search.meta.unavailable`. Zero results here are an OUTAGE, not a relevance
 * miss, so no trending/category suggestions — just the truth and a way back.
 */
const SearchUnavailableState: React.FC = () => (
  <div className="lm-isearch__empty" role="alert">
    <h2>Search is temporarily unavailable</h2>
    <p>We couldn&rsquo;t run your search just now. Please try again in a moment.</p>
    <div className="lm-isearch__empty-chips">
      <button type="button" className="lm-isearch__empty-chip" onClick={() => window.location.reload()}>
        Try again
      </button>
      <Link to="/" className="lm-isearch__empty-chip lm-isearch__empty-chip--cat">
        Back to the home page
      </Link>
    </div>
  </div>
);

export default SearchUnavailableState;
