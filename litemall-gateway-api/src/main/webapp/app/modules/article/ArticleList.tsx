import React, { useEffect, useState } from 'react';
import { Nav, Pagination, Spinner } from 'react-bootstrap';
import { Link } from 'react-router-dom';

import { contentApi, IArticle, IArticleCategory } from 'app/shared/api';

const PAGE_SIZE = 10;

/** addTime arrives as a LocalDateTime tuple ([y,m,d,...]) or an ISO string. */
const fmtDate = (addTime?: string | number[]): string => {
  if (Array.isArray(addTime) && addTime.length >= 3) {
    const [y, m, d] = addTime;
    return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
  }
  return typeof addTime === 'string' ? addTime.slice(0, 10) : '';
};

/**
 * Article CMS list (`/srv/article/list` + category tabs from
 * `/srv/article/categories` — goods-management Wave 4, handoff-content-endpoints
 * §1). Backend LIVE (Wave-4 merge, verified 2026-07-13); any transient failure
 * still degrades to a friendly empty state.
 */
const ArticleList: React.FC = () => {
  const [categories, setCategories] = useState<IArticleCategory[]>([]);
  const [categoryId, setCategoryId] = useState<number | undefined>(undefined);
  const [articles, setArticles] = useState<IArticle[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    contentApi
      .articleCategories()
      .then(res => setCategories(res?.list ?? []))
      .catch(() => setCategories([]));
  }, []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    contentApi
      .articleList(page, PAGE_SIZE, categoryId)
      .then(res => {
        if (cancelled) return;
        setArticles(res?.list ?? []);
        setTotal(res?.total ?? 0);
      })
      .catch(() => {
        if (!cancelled) {
          setArticles([]);
          setTotal(0);
        }
      })
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [page, categoryId]);

  const pages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  return (
    <div className='container my-4' style={{ maxWidth: 860 }}>
      <h1 className='h4 mb-3'>Articles</h1>

      {categories.length > 0 && (
        <Nav
          variant='tabs'
          className='mb-3'
          activeKey={categoryId == null ? 'all' : String(categoryId)}
          onSelect={k => {
            setPage(1);
            setCategoryId(k === 'all' || k == null ? undefined : Number(k));
          }}
        >
          <Nav.Item>
            <Nav.Link eventKey='all'>All</Nav.Link>
          </Nav.Item>
          {categories.map(c => (
            <Nav.Item key={c.id}>
              <Nav.Link eventKey={String(c.id)}>{c.name}</Nav.Link>
            </Nav.Item>
          ))}
        </Nav>
      )}

      {loading ? (
        <div className='text-center my-5'>
          <Spinner animation='border' />
        </div>
      ) : articles.length === 0 ? (
        <p className='text-muted my-5 text-center'>No articles yet.</p>
      ) : (
        <div className='list-group'>
          {articles.map(a => (
            <Link key={a.id} to={`/article/${a.id}`} className='list-group-item list-group-item-action d-flex gap-3'>
              {a.picUrl && (
                <img src={a.picUrl} alt='' style={{ width: 96, height: 64, objectFit: 'cover', borderRadius: 6 }} loading='lazy' />
              )}
              <div className='flex-fill'>
                <div className='fw-semibold'>
                  {a.isHot && <span className='badge text-bg-danger me-2'>Hot</span>}
                  {a.title}
                </div>
                {a.summary && <div className='small text-muted'>{a.summary}</div>}
                <div className='small text-muted'>
                  {a.categoryName && <span className='me-3'>{a.categoryName}</span>}
                  {fmtDate(a.addTime)}
                  {a.viewCount != null && <span className='ms-3'>{a.viewCount} views</span>}
                </div>
              </div>
            </Link>
          ))}
        </div>
      )}

      {pages > 1 && (
        <Pagination className='mt-3 justify-content-center'>
          <Pagination.Prev disabled={page <= 1} onClick={() => setPage(p => p - 1)} />
          <Pagination.Item active>{page}</Pagination.Item>
          <Pagination.Next disabled={page >= pages} onClick={() => setPage(p => p + 1)} />
        </Pagination>
      )}
    </div>
  );
};

export default ArticleList;
