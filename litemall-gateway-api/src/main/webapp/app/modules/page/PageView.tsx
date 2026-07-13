import React, { useEffect, useState } from 'react';
import { Spinner } from 'react-bootstrap';
import { Link, useParams } from 'react-router-dom';

import { contentApi, IPageView } from 'app/shared/api';
import PageRenderer from './PageRenderer';

/**
 * Custom DIY page route (`/page/:id` → `GET /srv/page/{id}`, spec §4).
 * errno 642 = missing/draft/deleted (drafts are never served customer-side);
 * 404/501 or network failure (backend not merged/running yet) renders the
 * same friendly not-available state.
 */
const PageView: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const [page, setPage] = useState<IPageView | null>(null);
  const [state, setState] = useState<'loading' | 'ok' | 'unavailable'>('loading');

  useEffect(() => {
    let cancelled = false;
    setState('loading');
    contentApi
      .pageById(id ?? '')
      .then(p => {
        if (cancelled) return;
        setPage(p);
        setState('ok');
      })
      .catch(() => {
        // 642 (not active), 404/501 (backend not shipped), or network failure.
        if (!cancelled) setState('unavailable');
      });
    return () => {
      cancelled = true;
    };
  }, [id]);

  if (state === 'loading') {
    return (
      <div className='text-center my-5'>
        <Spinner animation='border' />
      </div>
    );
  }
  if (state === 'unavailable' || !page) {
    return (
      <div className='container my-5 text-center'>
        <h4>This page isn’t available</h4>
        <p className='text-muted'>
          It may have been unpublished. <Link to='/'>Back to the home page</Link>
        </p>
      </div>
    );
  }
  return <PageRenderer page={page} />;
};

export default PageView;
