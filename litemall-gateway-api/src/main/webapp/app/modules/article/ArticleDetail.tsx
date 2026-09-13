import { Trans, useTranslation } from 'app/i18n';
import React, { useEffect, useState } from 'react';
import { Spinner } from 'react-bootstrap';
import { Link, useParams } from 'react-router-dom';

import { contentApi, IArticle } from 'app/shared/api';

/** addTime arrives as a LocalDateTime tuple ([y,m,d,...]) or an ISO string. */
const fmtDate = (addTime?: string | number[]): string => {
  if (Array.isArray(addTime) && addTime.length >= 3) {
    const [y, m, d] = addTime;
    return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
  }
  return typeof addTime === 'string' ? addTime.slice(0, 10) : '';
};

/**
 * Article detail (`/srv/article/detail?id=` — goods-management Wave 4).
 * `content` is server-sanitized HTML (jsoup Safelist on save) — safe to
 * inject. errno 643 = missing/hidden; 404/501/network degrade the same way.
 */
const ArticleDetail: React.FC = () => {
  const { t } = useTranslation('content');
  const { id } = useParams<{ id: string }>();
  const [article, setArticle] = useState<IArticle | null>(null);
  const [state, setState] = useState<'loading' | 'ok' | 'unavailable'>('loading');

  useEffect(() => {
    let cancelled = false;
    setState('loading');
    contentApi
      .articleDetail(id ?? '')
      .then(a => {
        if (cancelled) return;
        setArticle(a);
        setState('ok');
      })
      .catch(() => {
        // errno 643 (missing/hidden) or backend not shipped yet.
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
  if (state === 'unavailable' || !article) {
    return (
      <div className='container my-5 text-center'>
        <h4>{t('article.unavailable')}</h4>
        <p className='text-muted'>
          <Trans t={t} i18nKey='article.removed' components={{ 1: <Link to='/articles' /> }} />
        </p>
      </div>
    );
  }

  return (
    <div className='container my-4' style={{ maxWidth: 760 }}>
      <nav className='small mb-2'>
        <Link to='/articles' className='text-decoration-none'>
          {t('article.title')}
        </Link>
        {article.categoryName && <span className='text-muted'> / {article.categoryName}</span>}
      </nav>
      <h1 className='h3'>{article.title}</h1>
      <div className='small text-muted mb-3'>
        {fmtDate(article.addTime)}
        {article.viewCount != null && <span className='ms-3'>{t('article.views', { count: article.viewCount })}</span>}
      </div>
      {article.picUrl && <img src={article.picUrl} alt='' className='mb-3' style={{ maxWidth: '100%', borderRadius: 8 }} />}
      {/* Server-sanitized HTML (spec: clean-and-store on save). */}
      <div className='lm-article-content' dangerouslySetInnerHTML={{ __html: article.content ?? '' }} />
      {!!article.goodsId && article.goodsId > 0 && (
        <div className='mt-4 p-3 bg-light rounded'>
          <Link to={`/product/${article.goodsId}`} className='text-decoration-none'>
            <i className='bi bi-bag me-2' />
            {t('article.featuredProduct')}
          </Link>
        </div>
      )}
    </div>
  );
};

export default ArticleDetail;
