import React, { useEffect, useState } from 'react';

import { IComment, userApi, isMissingEndpoint } from 'app/shared/api';

/**
 * Product reviews/comments, mirroring litemall-vue's comment list on the detail
 * page (`/srv/comment/list`/`count`, type 0 = goods). Shows a count header, a
 * star rating per review, and the body. Hidden entirely until the endpoint is
 * live (follow-up: goods-management).
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
        setComments(res?.data ?? []);
        setCount(res?.count ?? (res?.data?.length ?? 0));
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

  return (
    <section className='lm-pdp__reviews'>
      <h3 className='lm-pdp__reviewstitle'>Customer reviews {count > 0 && <span>({count})</span>}</h3>
      {comments.length === 0 ? (
        <p className='text-muted'>No reviews yet. Be the first to review this product.</p>
      ) : (
        <ul className='lm-pdp__reviewlist'>
          {comments.map((c, i) => (
            <li key={c.id ?? i} className='lm-pdp__review'>
              <div className='lm-pdp__reviewhead'>
                <span className='lm-pdp__reviewer'>{c.nickName ?? 'Anonymous'}</span>
                <Stars n={c.star ?? 5} />
              </div>
              <p className='lm-pdp__reviewbody'>{c.content}</p>
              {c.picList && c.picList.length > 0 && (
                <div className='lm-pdp__reviewpics'>
                  {c.picList.map(p => (
                    <img key={p} src={p} alt='' />
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
