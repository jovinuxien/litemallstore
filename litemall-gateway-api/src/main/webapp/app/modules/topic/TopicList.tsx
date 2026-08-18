import React, { useEffect, useState } from 'react';
import { Spinner } from 'react-bootstrap';
import { Link } from 'react-router-dom';

import { ITopic } from 'app/shared/api';
import { loadTopicsWithGoods } from 'app/shared/util/contentAvailability';
import { secureImageUrl } from 'app/shared/util/imageUrl';
import { money } from 'app/shared/util/money';
import 'app/shared/scss/content.scss';

/**
 * Topics / specials directory, modelled on litemall-vue `items/topic-list`.
 * Sourced from `/srv/topic/list` (live on goods-management).
 *
 * Only topics that actually point at products are listed: the seed rows carry
 * an empty goods list, so listing them renders tiles that lead to an empty
 * topic page. The rule lives in `shared/util/contentAvailability.ts`, shared
 * with the nav entries that point here — a topic an admin fills with products
 * shows up in both without a rebuild.
 */
const TopicList: React.FC = () => {
  const [topics, setTopics] = useState<ITopic[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    loadTopicsWithGoods()
      .then(list => {
        if (!cancelled) setTopics(list);
      })
      .catch(() => {
        if (!cancelled) setTopics([]);
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
              {secureImageUrl(t.picUrl) && <img src={secureImageUrl(t.picUrl) as string} alt={t.title} />}
              <div className='lm-topic-row__info'>
                <div className='fw-semibold'>{t.title}</div>
                {t.subtitle && <div className='text-muted small'>{t.subtitle}</div>}
                {t.price != null && <div className='lm-topic-row__price'>from {money(t.price)}</div>}
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
};

export default TopicList;
