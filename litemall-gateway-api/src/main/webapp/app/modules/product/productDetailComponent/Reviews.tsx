import React, { useEffect, useState } from 'react';

import { IComment, userApi, isMissingEndpoint } from 'app/shared/api';

/**
 * Product reviews/comments, mirroring litemall-vue's comment list on the detail
 * page (`/srv/comment/list`, type 0 = goods; live on goods-management). Shows a
 * count header with the page's average stars, then each review with its rating,
 * reviewer, date and body. Local goods read litemall_comment; CJ goods are served
 * their CJ reviews through the same endpoint. Hidden only if the endpoint 404s.
 */
interface Props {
  goodsId?: number | string;
}

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
  const [count, setCount] = useState(0);
  const [available, setAvailable] = useState(true);

  useEffect(() => {
    if (goodsId == null) return;
    let cancelled = false;
    userApi
      .commentList(goodsId, 0, { page: 1, limit: 5 })
      .then(res => {
        if (cancelled) return;
        setComments(res?.list ?? []);
        setCount(res?.total ?? (res?.list?.length ?? 0));
      })
      .catch(e => {
        if (cancelled) return;
        if (isMissingEndpoint(e)) setAvailable(false);
      });
    return () => {
      cancelled = true;
    };
  }, [goodsId]);

  if (!available) return null;

  const avgStar = comments.length > 0 ? comments.reduce((s, c) => s + (c.star ?? 0), 0) / comments.length : 0;

  return (
    <section className='lm-pdp__reviews'>
      <h3 className='lm-pdp__reviewstitle'>
        Customer reviews {count > 0 && <span>({count})</span>}
        {comments.length > 0 && <Stars n={Math.round(avgStar)} />}
      </h3>
      {comments.length === 0 ? (
        <p className='text-muted'>No reviews yet. Be the first to review this product.</p>
      ) : (
        <ul className='lm-pdp__reviewlist'>
          {comments.map((c, i) => (
            <li key={i} className='lm-pdp__review'>
              <div className='lm-pdp__reviewhead'>
                <span className='lm-pdp__reviewer'>{c.userInfo?.nickName || 'Anonymous'}</span>
                <Stars n={c.star ?? 5} />
                {fmtDate(c.addTime) && <span className='lm-pdp__reviewdate text-muted'>{fmtDate(c.addTime)}</span>}
              </div>
              <p className='lm-pdp__reviewbody'>{c.content}</p>
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
      )}
    </section>
  );
};

export default Reviews;
