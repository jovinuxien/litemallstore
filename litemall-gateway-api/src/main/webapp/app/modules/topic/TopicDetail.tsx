import React, { useEffect, useState } from 'react';
import { Spinner } from 'react-bootstrap';
import { Link, useParams } from 'react-router-dom';

import ProductCard from 'app/components/userComponents/card/ProductCard';
import { contentApi, ITopic, isMissingEndpoint } from 'app/shared/api';
import { IGood } from 'app/shared/model/product/product.model';
import 'app/shared/scss/content.scss';

/**
 * Topic / special detail, modelled on litemall-vue `items/topic`: the topic
 * article content plus its curated products. Sourced from `/srv/topic/detail`.
 * Graceful when not live.
 */
const TopicDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const [topic, setTopic] = useState<ITopic | null>(null);
  const [goods, setGoods] = useState<IGood[]>([]);
  const [loading, setLoading] = useState(true);
  const [missing, setMissing] = useState(false);

  useEffect(() => {
    if (!id) return;
    let cancelled = false;
    setLoading(true);
    contentApi
      .topicDetail(id)
      .then((res: any) => {
        if (cancelled) return;
        setTopic(res?.topic ?? null);
        setGoods((res?.goods ?? []) as IGood[]);
      })
      .catch(e => {
        if (cancelled) return;
        if (isMissingEndpoint(e)) setMissing(true);
      })
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [id]);

  if (loading) {
    return (
      <div className='text-center my-5'>
        <Spinner animation='border' />
      </div>
    );
  }

  if (missing || !topic) {
    return (
      <div className='container my-5 text-center text-muted'>
        <p>This topic isn’t available right now.</p>
        <Link to='/topics' className='btn btn-outline-primary btn-sm'>
          All topics
        </Link>
      </div>
    );
  }

  return (
    <div className='container my-4' style={{ maxWidth: 860 }}>
      <Link to='/topics' className='btn btn-link px-0 mb-2'>
        <i className='bi bi-chevron-left' /> All topics
      </Link>
      <h1 className='h3'>{topic.title}</h1>
      {topic.subtitle && <p className='text-muted'>{topic.subtitle}</p>}
      {topic.picUrl && <img src={topic.picUrl} alt={topic.title} className='img-fluid rounded mb-3' />}
      {topic.content && <div className='lm-topic-content' dangerouslySetInnerHTML={{ __html: topic.content }} />}

      {goods.length > 0 && (
        <>
          <h2 className='h5 mt-4 mb-3'>Featured products</h2>
          <div className='lm-grid'>
            {goods.map((g, i) => (
              <ProductCard key={(g.id ?? i) as React.Key} product={g} />
            ))}
          </div>
        </>
      )}
    </div>
  );
};

export default TopicDetail;
