import React, { useEffect, useState } from 'react';
import { Navigate } from 'react-router-dom';
import { Spinner } from 'react-bootstrap';

import { IPageView } from 'app/shared/api';
import { loadSeason, seasonPath } from 'app/shared/util/season';

/**
 * Wave 27 — keeps the old `/summer` bookmark working.
 *
 * That path used to render a hardcoded `q=summer` keyword search. It is now
 * whatever season an admin has activated: the route resolves the season and
 * hands the visitor to its page. With no season running there is nothing
 * seasonal to show, so the visitor goes to the storefront home rather than to
 * an empty grid under a stale heading.
 *
 * This reads `loadSeason` directly rather than through `useSeason`, because it
 * needs to tell "still resolving" from "no season" — the nav can treat both as
 * absent, but a redirect cannot fire until it knows which one it is.
 */
const SeasonRedirect: React.FC = () => {
  const [state, setState] = useState<{ resolved: boolean; season: IPageView | null }>({ resolved: false, season: null });

  useEffect(() => {
    let cancelled = false;
    loadSeason().then(season => {
      if (!cancelled) setState({ resolved: true, season });
    });
    return () => {
      cancelled = true;
    };
  }, []);

  if (!state.resolved) {
    return (
      <div className='text-center my-5'>
        <Spinner animation='border' />
      </div>
    );
  }
  return <Navigate to={state.season ? seasonPath(state.season) : '/'} replace />;
};

export default SeasonRedirect;
