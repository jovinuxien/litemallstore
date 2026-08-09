import React, { useCallback, useEffect, useState } from 'react';

import { IComment, userApi } from 'app/shared/api';
import { REVIEWS_ANCHOR_ID } from './RatingSummary';
import { computeReviewStats } from './reviewStats';
import ReviewForm from './ReviewForm';

/**
 * Product reviews/comments (`/srv/comment/list`, type 0 = goods; local goods
 * read litemall_comment, CJ goods are served their CJ reviews through the same
 * endpoint). Amazon-style summary: big average + a 5→1 star histogram computed
 * client-side from a bulk page (limit 100 — the CJ ingest caps at ~60 per
 * product, so that is normally every review), then the list revealed 5 at a
 * time via "Show more reviews", fetching further pages once the local buffer
 * runs out. Signed-in customers get a "write a review" form feeding
 * `POST /srv/comment/post`.
 */
interface Props {
  goodsId?: number | string;
}

const FETCH_LIMIT = 100;
const INITIAL_VISIBLE = 5;
const VISIBLE_STEP = 10;

const Stars: React.FC<{ n: number }> = ({ n }) => (
  <span className='lm-pdp__stars' aria-label={`${n} of 5`}>
    {[1, 2, 3, 4, 5].map(i => (
      <i key={i} className={`bi ${i <= n ? 'bi-star-fill' : 'bi-star'}`} />
    ))}
  </span>
);

/** addTime arrives as a LocalDateTime tuple ([y,m,d,...], local rows) or an ISO string (CJ). */
const fmtDate = (addTime?: string | number[]): string => {
  if (Array.isArray(addTime) && addTime.length >= 3) {
    const [y, m, d] = addTime;
    return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
  }
  return typeof addTime === 'string' ? addTime.slice(0, 10) : '';
};

const Reviews: React.FC<Props> = ({ goodsId }) => {
  const [comments, setComments] = useState<IComment[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [visible, setVisible] = useState(INITIAL_VISIBLE);
  const [writing, setWriting] = useState(false);
  const signedIn = !!sessionStorage.getItem('customerToken');

  const fetchPage = useCallback(
    (p: number, replace: boolean) => {
      if (goodsId == null) return;
      userApi
        .commentList(goodsId, 0, { page: p, limit: FETCH_LIMIT })
        .then(res => {
          const list = res?.list ?? [];
          setComments(prev => (replace ? list : [...prev, ...list]));
          setTotal(res?.total ?? list.length);
          setPage(p);
        })
        .catch(() => undefined);
    },
    [goodsId]
  );

  useEffect(() => {
    setComments([]);
    setTotal(0);
    setVisible(INITIAL_VISIBLE);
    fetchPage(1, true);
  }, [fetchPage]);

  const refresh = useCallback(() => fetchPage(1, true), [fetchPage]);

  const stats = computeReviewStats(comments);
  const shown = comments.slice(0, visible);
  const hasMore = visible < comments.length || comments.length < total;

  const showMore = () => {
    const next = visible + VISIBLE_STEP;
    // Top up the local buffer when the reveal would outrun what's fetched.
    if (next > comments.length && comments.length < total) fetchPage(page + 1, false);
    setVisible(next);
  };

  return (
    <section className='lm-pdp__reviews' id={REVIEWS_ANCHOR_ID}>
      <h3 className='lm-pdp__reviewstitle'>
        Customer reviews
        {signedIn && goodsId != null && (
          <button type='button' className='btn btn-sm btn-lm-outline ms-3 align-middle' onClick={() => setWriting(w => !w)}>
            {writing ? 'Close' : 'Write a review'}
          </button>
        )}
      </h3>
      {writing && goodsId != null && (
        <div className='mb-3'>
          <ReviewForm goodsId={goodsId} onSubmitted={refresh} />
        </div>
      )}
      {comments.length === 0 ? (
        <p className='text-muted'>No reviews yet. Be the first to review this product.</p>
      ) : (
        <>
          <div className='lm-pdp__revsummary'>
            <div className='lm-pdp__revavg'>
              <Stars n={Math.round(stats.average)} />
              <span className='lm-pdp__revavgnum'>{stats.average.toFixed(1)} out of 5</span>
              <span className='lm-pdp__revtotal text-muted'>
                {total.toLocaleString()} rating{total === 1 ? '' : 's'}
              </span>
            </div>
            <ul className='lm-pdp__histo' aria-label='Rating breakdown'>
              {stats.bars.map(b => (
                <li key={b.star}>
                  <span className='lm-pdp__histo-label'>{b.star} star</span>
                  <span className='lm-pdp__histo-bar'>
                    <span className='lm-pdp__histo-fill' style={{ width: `${b.pct}%` }} />
                  </span>
                  <span className='lm-pdp__histo-pct'>{b.pct}%</span>
                </li>
              ))}
            </ul>
          </div>
          <ul className='lm-pdp__reviewlist'>
            {shown.map((c, i) => (
              <li key={i} className='lm-pdp__review'>
                <div className='lm-pdp__reviewhead'>
                  <span className='lm-pdp__reviewer'>{c.userInfo?.nickName || 'Anonymous'}</span>
                  <Stars n={c.star ?? 5} />
                  {fmtDate(c.addTime) && <span className='lm-pdp__reviewdate text-muted'>{fmtDate(c.addTime)}</span>}
                </div>
                <p className='lm-pdp__reviewbody'>{c.content}</p>
                {c.adminContent && (
                  <div className='p-2 mb-2 bg-light rounded small'>
                    <span className='fw-semibold'>
                      <i className='bi bi-shop me-1' />
                      Seller response:
                    </span>{' '}
                    {c.adminContent}
                  </div>
                )}
                {c.picList && c.picList.length > 0 && (
                  <div className='lm-pdp__reviewpics'>
                    {c.picList.map(p => (
                      // Seed data carries dead image hosts — hide broken review images.
                      <img key={p} src={p} alt='' onError={e => (e.currentTarget.style.display = 'none')} />
                    ))}
                  </div>
                )}
              </li>
            ))}
          </ul>
          {hasMore && (
            <button type='button' className='lm-pdp__revmore' onClick={showMore}>
              Show more reviews
            </button>
          )}
        </>
      )}
    </section>
  );
};

export default Reviews;
