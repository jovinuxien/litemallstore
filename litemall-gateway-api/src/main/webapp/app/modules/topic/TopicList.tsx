import React, { useEffect, useState } from 'react';
import { Spinner } from 'react-bootstrap';
import { Link } from 'react-router-dom';

import { contentApi, ITopic, isMissingEndpoint } from 'app/shared/api';
import 'app/shared/scss/content.scss';

/**
 * Topics / specials directory, modelled on litemall-vue `items/topic-list`.
 * Sourced from `/srv/topic/list`. Graceful empty until live.
 */
const TopicList: React.FC = () => {
  const [topics, setTopics] = useState<ITopic[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    contentApi
      .topicList({ page: 1, limit: 20 })
      .then(res => {
        if (!cancelled) setTopics(res?.list ?? []);
      })
      .catch(e => {
        if (!cancelled && !isMissingEndpoint(e)) setTopics([]);
      })
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className='container my-4'>
      <h1 className='h4 mb-3'>Topics</h1>
      {loading ? (
        <div className='text-center my-5'>
          <Spinner animation='border' />
        </div>
      ) : topics.length === 0 ? (
        <p className='text-muted text-center my-5'>No topics to show yet.</p>
      ) : (
        <div className='d-grid gap-3'>
          {topics.map(t => (
            <Link key={t.id} to={`/topic/${t.id}`} className='lm-topic-row'>
              {t.picUrl && <img src={t.picUrl} alt={t.title} />}
              <div className='lm-topic-row__info'>
                <div className='fw-semibold'>{t.title}</div>
                {t.subtitle && <div className='text-muted small'>{t.subtitle}</div>}
                {t.price != null && <div className='lm-topic-row__price'>from ${t.price}</div>}
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
};

export default TopicList;
