import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Form, Spinner } from 'react-bootstrap';
import { Link } from 'react-router-dom';

import { priceNum } from 'app/components/userComponents/card/ProductCard';
import { contentApi, promotionApi, ICombinationPink, IGrouponItem } from 'app/shared/api';
import 'app/shared/scss/content.scss';

/** LocalDateTime arrives as an ISO string or a Jackson number[] tuple. */
const toDisplayTime = (t?: string | number[]): string => {
  if (Array.isArray(t)) {
    const [y, mo, d, h = 0, mi = 0] = t;
    return `${y}-${String(mo).padStart(2, '0')}-${String(d).padStart(2, '0')} ${String(h).padStart(2, '0')}:${String(mi).padStart(2, '0')}`;
  }
  return t ? String(t).replace('T', ' ').slice(0, 16) : '';
};

/**
 * Group-buy (groupon) page, modelled on litemall-vue `items/groupon`. Browse is
 * sourced from `/srv/groupon/list` (the promotion service's legacy-shape
 * controller); the interactive flow — start a group / join by invite / my
 * groups — uses the canonical `/srv/promotion/combination` surface
 * (spec-groupon-priced-submit-contract.md). Ordering AT the group price is the
 * order service's pending `pinkId` submit-field — until it lands, a completed
 * group is browsable here but checkout still prices at retail.
 */
const Groupon: React.FC = () => {
  const [items, setItems] = useState<IGrouponItem[]>([]);
  const [loading, setLoading] = useState(true);

  // My slots ("pinks") — needs a signed-in customer; the canonical endpoints
  // read identity from the gateway-injected X-User-Id.
  const signedIn = !!sessionStorage.getItem('customerToken');
  const [myGroups, setMyGroups] = useState<ICombinationPink[]>([]);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionBusy, setActionBusy] = useState(false);
  const [joinPinkId, setJoinPinkId] = useState('');

  const refreshMyGroups = useCallback(() => {
    if (!signedIn) return;
    promotionApi
      .combinationMy()
      .then(list => setMyGroups(list ?? []))
      .catch(() => setMyGroups([]));
  }, [signedIn]);

  useEffect(() => {
    let cancelled = false;
    contentApi
      .grouponList({ page: 1, limit: 20 })
      .then(res => {
        if (!cancelled) setItems(res?.list ?? []);
      })
      .catch(() => {
        if (!cancelled) setItems([]);
      })
      .finally(() => !cancelled && setLoading(false));
    refreshMyGroups();
    return () => {
      cancelled = true;
    };
  }, [refreshMyGroups]);

  /** Run a start/join mutation; 400 carries {success:false,message}. Returns success. */
  const runAction = async (action: () => Promise<unknown>): Promise<boolean> => {
    setActionError(null);
    setActionBusy(true);
    try {
      await action();
      refreshMyGroups();
      return true;
    } catch (e) {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setActionError(msg ?? 'The group action could not be completed.');
      return false;
    } finally {
      setActionBusy(false);
    }
  };

  const startGroup = (combinationId?: number) => {
    if (combinationId != null) void runAction(() => promotionApi.combinationStart(combinationId));
  };
  const joinGroup = async () => {
    const pinkId = Number(joinPinkId);
    if (!Number.isFinite(pinkId) || pinkId <= 0) return;
    const ok = await runAction(() => promotionApi.combinationJoin(pinkId));
    if (ok) setJoinPinkId('');
  };

  return (
    <div className='container my-4'>
      <h1 className='h4 mb-3'>Group deals</h1>

      {signedIn && (myGroups.length > 0 || actionError) && (
        <div className='mb-4'>
          <h2 className='h6 text-muted'>My groups</h2>
          {actionError && (
            <Alert variant='warning' onClose={() => setActionError(null)} dismissible>
              {actionError}
            </Alert>
          )}
          <div className='d-grid gap-2'>
            {myGroups.map(p => (
              <div key={p.pinkId} className='border rounded p-2 d-flex flex-wrap justify-content-between align-items-center gap-2'>
                <div>
                  <span className='fw-semibold'>Group #{p.pinkId}</span>
                  {p.headId != null && p.headId === p.pinkId && <span className='badge text-bg-info ms-2'>Leader</span>}
                  <span className='ms-2'>
                    {p.memberCount ?? 1}/{p.requiredMembers ?? '?'} joined
                  </span>
                  <span className={`badge ms-2 ${p.status === 'Success' ? 'text-bg-success' : p.status === 'Failed' ? 'text-bg-secondary' : 'text-bg-warning'}`}>
                    {p.status ?? 'Pending'}
                  </span>
                </div>
                <div className='small text-muted'>
                  {p.status === 'Pending' && p.expireTime ? <>expires {toDisplayTime(p.expireTime)} · share id #{p.pinkId} to invite</> : null}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {signedIn && (
        <Form
          className='d-flex gap-2 align-items-center mb-4'
          onSubmit={e => {
            e.preventDefault();
            void joinGroup();
          }}
        >
          <Form.Control
            size='sm'
            style={{ maxWidth: 220 }}
            placeholder='Join with an invite id…'
            value={joinPinkId}
            onChange={e => setJoinPinkId(e.target.value)}
            inputMode='numeric'
          />
          <Button size='sm' variant='outline-primary' type='submit' disabled={actionBusy || !joinPinkId}>
            Join group
          </Button>
        </Form>
      )}

      {loading ? (
        <div className='text-center my-5'>
          <Spinner animation='border' />
        </div>
      ) : items.length === 0 ? (
        <p className='text-muted text-center my-5'>No group deals running right now.</p>
      ) : (
        <div className='lm-grid'>
          {items.map((g, i) => (
            <div key={g.id ?? i} className='lm-groupon-card d-flex flex-column'>
              <Link to={`/product/${g.goodsId}`} className='text-reset text-decoration-none'>
                <img src={g.picUrl} alt={g.goodsName} />
                <div className='lm-groupon-card__name'>{g.goodsName}</div>
                <div className='lm-groupon-card__price'>
                  <span className='lm-groupon-card__now'>${priceNum(g.grouponPrice ?? g.retailPrice ?? 0).toFixed(2)}</span>
                  {g.discount != null && <span className='lm-groupon-card__badge'>−${g.discount}</span>}
                </div>
              </Link>
              <div className='small text-muted mt-1'>{g.discountMember ?? '?'} people per group</div>
              {signedIn && (
                <Button size='sm' variant='outline-primary' className='mt-2' disabled={actionBusy} onClick={() => startGroup(g.id)}>
                  Start a group
                </Button>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default Groupon;
