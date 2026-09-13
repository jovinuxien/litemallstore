import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Form, Spinner } from 'react-bootstrap';
import { Link } from 'react-router-dom';

import { priceNum } from 'app/components/userComponents/card/ProductCard';
import { money } from 'app/shared/util/money';
import { useTranslation } from 'app/i18n';
import { contentApi, promotionApi, ICombinationPink, IGrouponItem } from 'app/shared/api';
import { toDisplayTime } from './grouponUtils';
import 'app/shared/scss/content.scss';

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
  const { t } = useTranslation('content');
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
      setActionError(msg ?? t('groupon.actionFailed'));
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
      <h1 className='h4 mb-3'>{t('groupon.title')}</h1>

      {signedIn && (myGroups.length > 0 || actionError) && (
        <div className='mb-4'>
          <h2 className='h6 text-muted'>{t('groupon.myGroups')}</h2>
          {actionError && (
            <Alert variant='warning' onClose={() => setActionError(null)} dismissible>
              {actionError}
            </Alert>
          )}
          <div className='d-grid gap-2'>
            {myGroups.map(p => (
              <div key={p.pinkId} className='border rounded p-2 d-flex flex-wrap justify-content-between align-items-center gap-2'>
                <div>
                  {/* Wave-21: each slot deep-links its campaign landing, where the
                      share link + group-price checkout live. */}
                  {p.combinationId != null ? (
                    <Link to={`/groupon/${p.combinationId}`} className='fw-semibold'>
                      {t('groupon.groupN', { id: p.pinkId })}
                    </Link>
                  ) : (
                    <span className='fw-semibold'>{t('groupon.groupN', { id: p.pinkId })}</span>
                  )}
                  {p.headId != null && p.headId === p.pinkId && <span className='badge text-bg-info ms-2'>{t('groupon.leader')}</span>}
                  <span className='ms-2'>{t('groupon.joined', { count: p.memberCount ?? 1, required: p.requiredMembers ?? '?' })}</span>
                  <span className={`badge ms-2 ${p.status === 'Success' ? 'text-bg-success' : p.status === 'Failed' ? 'text-bg-secondary' : 'text-bg-warning'}`}>
                    {p.status ?? t('groupon.pending')}
                  </span>
                </div>
                <div className='small text-muted'>
                  {p.status === 'Pending' && p.expireTime ? t('groupon.expiresShare', { time: toDisplayTime(p.expireTime), id: p.pinkId }) : null}
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
            placeholder={t('groupon.joinPlaceholder')}
            value={joinPinkId}
            onChange={e => setJoinPinkId(e.target.value)}
            inputMode='numeric'
          />
          <Button size='sm' variant='outline-primary' type='submit' disabled={actionBusy || !joinPinkId}>
            {t('groupon.join')}
          </Button>
        </Form>
      )}

      {loading ? (
        <div className='text-center my-5'>
          <Spinner animation='border' />
        </div>
      ) : items.length === 0 ? (
        <p className='text-muted text-center my-5'>{t('groupon.none')}</p>
      ) : (
        <div className='lm-grid'>
          {items.map((g, i) => (
            <div key={g.id ?? i} className='lm-groupon-card d-flex flex-column'>
              {/* Wave-21: cards deep-link the shareable /groupon/:id landing
                  (start/join/share live there); no campaign id ⇒ product page. */}
              <Link to={g.id != null ? `/groupon/${g.id}` : `/product/${g.goodsId}`} className='text-reset text-decoration-none'>
                <img src={g.picUrl} alt={g.goodsName} />
                <div className='lm-groupon-card__name'>{g.goodsName}</div>
                <div className='lm-groupon-card__price'>
                  <span className='lm-groupon-card__now'>{money(priceNum(g.grouponPrice ?? g.retailPrice ?? 0))}</span>
                  {g.discount != null && <span className='lm-groupon-card__badge'>−{money(g.discount)}</span>}
                </div>
              </Link>
              <div className='small text-muted mt-1'>{t('groupon.perGroup', { count: Number(g.discountMember) || 0 })}</div>
              {g.id != null ? (
                <Link to={`/groupon/${g.id}`} className='btn btn-sm btn-outline-primary mt-2'>
                  {t('groupon.viewDeal')}
                </Link>
              ) : (
                signedIn && (
                  <Button size='sm' variant='outline-primary' className='mt-2' disabled={actionBusy} onClick={() => startGroup(g.id)}>
                    {t('groupon.start')}
                  </Button>
                )
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default Groupon;
