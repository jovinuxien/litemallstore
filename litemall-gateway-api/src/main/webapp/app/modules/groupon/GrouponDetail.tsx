import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Spinner } from 'react-bootstrap';
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom';

import { priceNum } from 'app/components/userComponents/card/ProductCard';
import { money } from 'app/shared/util/money';
import { EmptyState, Page, PageHead } from 'app/components/commonComponents/storefront';
import { useAppSelector } from 'app/config/store';
import { Trans, useTranslation } from 'app/i18n';
import { ICombination, ICombinationPink, promotionApi } from 'app/shared/api';
import { productPath } from 'app/shared/util/slug';
import { resetPageTitle, setPageTitle } from 'app/shared/util/pageTitle';
import {
  activeSlotFor,
  canInvite,
  fmtTimeLeft,
  inviteLeaderId,
  inviteLink,
  serverTimeToEpoch,
  slotCta,
  toDisplayTime,
} from './grouponUtils';
import 'app/shared/scss/content.scss';

/**
 * Wave-21 shareable group-buy landing (`/groupon/:id`, :id = combinationId).
 * One campaign: goods image/name, group price vs original, required members,
 * time left, the visitor's own group progress when they hold a slot, and the
 * Start-a-group / Join CTAs. A shared URL carries the leader's slot as
 * `?join=<leaderPinkId>` so a friend can join that group directly.
 *
 * Start/Join reserve the slot server-side FIRST, then route to the product
 * page with `?pinkId=<own slot>` — the shopper picks options there and
 * Buy-now carries the pinkId into `/checkout?pinkId=`, where submit charges
 * the group price server-side (never computed client-side here).
 */
const GrouponDetail: React.FC = () => {
  const { t } = useTranslation('content');
  const { id } = useParams<{ id: string }>();
  const combinationId = Number(id);
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const joinId = Number(searchParams.get('join'));
  const hasJoinLink = Number.isFinite(joinId) && joinId > 0;

  const isAuthenticated = useAppSelector(state => state.customerAuth.data.isAuthenticated);

  const [campaign, setCampaign] = useState<ICombination | null>(null);
  const [loading, setLoading] = useState(true);
  const [myGroups, setMyGroups] = useState<ICombinationPink[]>([]);
  // The invited group's live progress (fail-soft: the join CTA works without it).
  const [joinTarget, setJoinTarget] = useState<ICombinationPink | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionBusy, setActionBusy] = useState(false);
  const [copied, setCopied] = useState(false);

  // 30s tick keeps the countdowns honest while the page is open.
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 30000);
    return () => clearInterval(t);
  }, []);

  const refreshMyGroups = useCallback(() => {
    if (!isAuthenticated) return;
    promotionApi
      .combinationMy()
      .then(list => setMyGroups(list ?? []))
      .catch(() => setMyGroups([]));
  }, [isAuthenticated]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    if (!Number.isFinite(combinationId) || combinationId <= 0) {
      setCampaign(null);
      setLoading(false);
      return undefined;
    }
    promotionApi
      .combinationDetail(combinationId)
      .then(c => {
        if (cancelled) return;
        setCampaign(c ?? null);
        if (c?.title) setPageTitle(t('groupon.pageTitle', { title: c.title }));
      })
      .catch(() => !cancelled && setCampaign(null))
      .finally(() => !cancelled && setLoading(false));
    refreshMyGroups();
    return () => {
      cancelled = true;
      resetPageTitle();
    };
  }, [combinationId, refreshMyGroups]);

  // Invited group preview — an auth-only read, so it is best-effort for
  // logged-out visitors (the join button still works after sign-in).
  useEffect(() => {
    if (!hasJoinLink || !isAuthenticated) return undefined;
    let cancelled = false;
    promotionApi
      .combinationPink(joinId)
      .then(p => !cancelled && setJoinTarget(p ?? null))
      .catch(() => !cancelled && setJoinTarget(null));
    return () => {
      cancelled = true;
    };
  }, [hasJoinLink, joinId, isAuthenticated]);

  const mySlot = useMemo(() => activeSlotFor(myGroups, combinationId, now), [myGroups, combinationId, now]);
  const myCta = slotCta(mySlot, now);

  const goodsId = campaign?.goodsId;
  const productTo = goodsId != null ? productPath(goodsId, campaign?.title ?? '') : '/search';
  const groupPrice = priceNum(campaign?.combinationPrice);
  const originalPrice = priceNum(campaign?.originalPrice);
  const required = campaign?.requiredMembers;
  const endsIn = fmtTimeLeft(serverTimeToEpoch(campaign?.endTime), now);

  const requireSignIn = (): boolean => {
    if (isAuthenticated) return false;
    // CustomerLogin navigates back to from.pathname — carry the query (the
    // ?join= deep-link) inside it so the invite survives the sign-in hop.
    navigate('/login', { state: { from: { pathname: `${location.pathname}${location.search}` } } });
    return true;
  };

  /** Run start/join; a 400 carries {success:false,message} — shown verbatim. */
  const runAction = async (action: () => Promise<{ data?: Record<string, unknown> }>): Promise<number | null> => {
    setActionError(null);
    setActionBusy(true);
    try {
      const op = await action();
      const pinkId = Number(op?.data?.pinkId);
      refreshMyGroups();
      return Number.isFinite(pinkId) && pinkId > 0 ? pinkId : null;
    } catch (e) {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setActionError(msg ?? t('groupon.actionFailed'));
      return null;
    } finally {
      setActionBusy(false);
    }
  };

  /** Slot in hand → pick options on the product page, Buy-now carries the pinkId. */
  const toProductWithSlot = (pinkId: number) => {
    navigate(`${productTo}${productTo.includes('?') ? '&' : '?'}pinkId=${pinkId}`);
  };

  const startGroup = async () => {
    if (requireSignIn() || campaign?.combinationId == null) return;
    const pinkId = await runAction(() => promotionApi.combinationStart(campaign.combinationId!));
    if (pinkId != null) toProductWithSlot(pinkId);
  };

  const joinGroup = async () => {
    if (requireSignIn() || !hasJoinLink) return;
    const pinkId = await runAction(() => promotionApi.combinationJoin(joinId));
    if (pinkId != null) toProductWithSlot(pinkId);
  };

  const copyInvite = async (leaderId: number) => {
    const url = `${window.location.origin}${inviteLink(combinationId, leaderId)}`;
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
      setTimeout(() => setCopied(false), 1600);
    } catch {
      // Clipboard unavailable (http / permissions) — show the link itself.
      window.prompt(t('groupon.copyPrompt'), url);
    }
  };

  if (loading) {
    return (
      <Page>
        <div className='text-center my-5'>
          <Spinner animation='border' />
        </div>
      </Page>
    );
  }

  if (!campaign) {
    return (
      <Page>
        <PageHead title={t('groupon.deal')} />
        <div className='container'>
          <EmptyState icon='bi-people' text={t('groupon.gone')}>
            <Link to='/groupon' className='btn btn-sm btn-outline-primary mt-2'>
              {t('groupon.seeCurrent')}
            </Link>
          </EmptyState>
        </div>
      </Page>
    );
  }

  const memberCount = mySlot?.memberCount ?? 1;
  const slotRequired = mySlot?.requiredMembers ?? required;
  const progressPct = slotRequired ? Math.min(100, Math.round((memberCount / slotRequired) * 100)) : 0;
  const myLeaderId = mySlot ? inviteLeaderId(mySlot) : undefined;

  return (
    <Page>
      <PageHead
        title={t('groupon.deal')}
        sub={<Trans t={t} i18nKey='groupon.sub' values={{ required: required ?? t('groupon.enough') }} components={{ 1: <Link to='/groupon' /> }} />}
      />
      <div className='container my-3' style={{ maxWidth: 720 }}>
        {/* Campaign card */}
        <div className='border rounded p-3 d-flex gap-3 flex-wrap align-items-start'>
          <Link to={productTo}>
            <img
              src={campaign.picUrl}
              alt={campaign.title}
              style={{ width: 140, height: 140, objectFit: 'cover', borderRadius: 8 }}
            />
          </Link>
          <div className='flex-grow-1' style={{ minWidth: 220 }}>
            <Link to={productTo} className='text-reset text-decoration-none'>
              <div className='fw-semibold'>{campaign.title}</div>
            </Link>
            <div className='mt-2 d-flex align-items-baseline gap-2 flex-wrap'>
              <span className='fs-4 fw-bold' style={{ color: 'var(--lm-primary, #0d7d80)' }}>
                {money(groupPrice)}
              </span>
              {originalPrice > groupPrice && (
                <span className='text-muted'>
                  <s>{money(originalPrice)}</s> {t('groupon.regular')}
                </span>
              )}
            </div>
            <div className='small text-muted mt-1'>
              {required != null && t('groupon.perGroup', { count: required })}
              {endsIn && t('groupon.endsIn', { time: endsIn })}
            </div>
            <div className='small mt-1'>
              <Link to={productTo}>{t('groupon.viewProduct')}</Link>
            </div>
          </div>
        </div>

        {actionError && (
          <Alert variant='warning' className='mt-3' onClose={() => setActionError(null)} dismissible>
            {actionError}
          </Alert>
        )}

        {/* The visitor's own group for this campaign */}
        {mySlot && (
          <div className='border rounded p-3 mt-3'>
            <div className='d-flex justify-content-between align-items-center flex-wrap gap-2'>
              <div>
                <span className='fw-semibold'>{t('groupon.yourGroup')}</span>
                {mySlot.headId != null && mySlot.headId === mySlot.pinkId && <span className='badge text-bg-info ms-2'>{t('groupon.leader')}</span>}
                <span
                  className={`badge ms-2 ${
                    myCta === 'ordered' || mySlot.status === 'Success'
                      ? 'text-bg-success'
                      : myCta === 'expired'
                        ? 'text-bg-secondary'
                        : 'text-bg-warning'
                  }`}
                >
                  {myCta === 'expired' ? t('groupon.expired') : (mySlot.status ?? t('groupon.pending'))}
                </span>
              </div>
              <div className='small text-muted'>
                {myCta !== 'expired' && mySlot.status === 'Pending' && mySlot.expireTime ? (
                  t('groupon.expires', { time: toDisplayTime(mySlot.expireTime) })
                ) : null}
              </div>
            </div>

            <div className='mt-2 d-flex align-items-center gap-2'>
              <div className='flex-grow-1' style={{ height: 8, borderRadius: 4, background: '#f1f3f5', overflow: 'hidden' }}>
                <div style={{ width: `${progressPct}%`, height: '100%', background: 'var(--lm-primary, #0d7d80)' }} />
              </div>
              <span className='small text-nowrap'>
                {t('groupon.joined', { count: memberCount, required: slotRequired ?? '?' })}
              </span>
            </div>

            <div className='mt-3 d-flex gap-2 flex-wrap'>
              {myCta === 'checkout' && mySlot.pinkId != null && (
                <Button size='sm' variant='primary' onClick={() => toProductWithSlot(mySlot.pinkId!)}>
                  {t('groupon.buyNowAt', { price: money(groupPrice) })}
                </Button>
              )}
              {canInvite(mySlot, now) && myLeaderId != null && (
                <Button size='sm' variant='outline-primary' onClick={() => void copyInvite(myLeaderId)}>
                  {copied ? t('groupon.linkCopied') : t('groupon.copyInvite')}
                </Button>
              )}
              {myCta === 'ordered' && (
                <span className='small text-success align-self-center'>
                  <Trans t={t} i18nKey='groupon.orderPlaced' components={{ 1: <Link to='/orders' /> }} />
                </span>
              )}
            </div>

            {myCta === 'expired' && (
              <Alert variant='secondary' className='mt-3 mb-0'>
                <Trans t={t} i18nKey='groupon.groupExpired' components={{ 1: <Link to={productTo} /> }} />
              </Alert>
            )}
          </div>
        )}

        {/* Join deep-link: a friend arriving via a shared URL */}
        {hasJoinLink && myCta !== 'checkout' && myCta !== 'ordered' && (
          <div className='border rounded p-3 mt-3'>
            <div className='fw-semibold'>{t('groupon.invited')}</div>
            {joinTarget ? (
              <div className='small text-muted mt-1'>
                {t('groupon.joined', { count: joinTarget.memberCount ?? 1, required: joinTarget.requiredMembers ?? required ?? '?' })}
                {joinTarget.expireTime && slotCta(joinTarget, now) !== 'expired' && ` · ${t('groupon.expires', { time: toDisplayTime(joinTarget.expireTime) })}`}
              </div>
            ) : (
              <div className='small text-muted mt-1'>{t('groupon.joinPitch')}</div>
            )}
            <Button size='sm' variant='primary' className='mt-2' disabled={actionBusy} onClick={() => void joinGroup()}>
              {isAuthenticated ? t('groupon.joinAt', { price: money(groupPrice) }) : t('groupon.signInToJoin')}
            </Button>
          </div>
        )}

        {/* Start a group */}
        {myCta !== 'checkout' && myCta !== 'ordered' && (
          <div className='mt-3 d-flex gap-2 flex-wrap'>
            <Button variant={hasJoinLink ? 'outline-primary' : 'primary'} disabled={actionBusy} onClick={() => void startGroup()}>
              {isAuthenticated ? t('groupon.start') : t('groupon.signInToStart')}
            </Button>
            <Link to={productTo} className='btn btn-outline-secondary'>
              {t('groupon.buyRegular')}
            </Link>
          </div>
        )}
      </div>
    </Page>
  );
};

export default GrouponDetail;
