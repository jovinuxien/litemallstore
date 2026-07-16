import {
  ICampaign,
  promotionOpMessage,
  useActivateCampaignMutation,
  useEvaluateCampaignMutation,
  useListCampaignsQuery,
} from 'app/shared/reducers/private/services/adminPromotionApi';
import { ElTag, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';

// Targeting campaigns (promotion-service Phase 2), authenticated admin →
// /srv/private/admin/promotion/campaign. List + activate/evaluate; creation
// stays API-first (define is a criteria-rich POST — no form here yet).
//
// Wave 6: per-campaign "Segment in Mautic" link-out. Mautic's delivery
// adapter names each campaign's segment `litemall-campaign-<id>` (see
// MauticDeliveryAdapter/MauticProperties), so the link opens Mautic's segment
// list pre-filtered to it. Shown only when the SPA build carries
// MAUTIC_BASE_URL (webpack env) — hidden otherwise.

const STATUS_TAG: Record<string, ElTag> = {
  draft: 'info',
  active: 'success',
  expired: 'warning',
  cancelled: 'danger',
  offline: 'danger',
};

const mauticSegmentUrl = (campaignId?: number): string | undefined => {
  if (!MAUTIC_BASE_URL || campaignId == null) return undefined;
  return `${MAUTIC_BASE_URL.replace(/\/+$/, '')}/s/segments?search=litemall-campaign-${campaignId}`;
};

const window_ = (c: ICampaign): string => {
  const f = (v?: string) => (v ? v.replace('T', ' ').slice(0, 16) : '…');
  return c.startTime || c.endTime ? `${f(c.startTime)} → ${f(c.endTime)}` : '—';
};

const CampaignList: React.FC = () => {
  const { data, isLoading, isFetching, isError, error } = useListCampaignsQuery();
  const [activate, { isLoading: activating }] = useActivateCampaignMutation();
  const [evaluate, { isLoading: evaluating }] = useEvaluateCampaignMutation();
  const [actionError, setActionError] = React.useState<string | null>(null);
  const [actionInfo, setActionInfo] = React.useState<string | null>(null);

  const list = data?.list ?? [];
  const errStatus = (error as { status?: number | string })?.status;
  const busy = activating || evaluating;

  const run = async (kind: 'activate' | 'evaluate', c: ICampaign) => {
    if (c.id == null) return;
    setActionError(null);
    setActionInfo(null);
    const res = await (kind === 'activate' ? activate(c.id) : evaluate(c.id));
    const msg = promotionOpMessage(res);
    if (msg) setActionError(msg);
    else if (kind === 'evaluate') setActionInfo(`Campaign "${c.name ?? c.id}" evaluated — audience assignment updated.`);
  };

  return (
    <div className='app-container'>
      <div className='filter-container'>
        <span className='text-muted small filter-item'>
          Campaigns are defined via the promotion API; activate + evaluate run from here.
        </span>
        {isFetching && <Spinner />}
      </div>

      {isError && <div className='alert alert-danger'>Failed to load campaigns{errStatus ? ` (${errStatus})` : ''}.</div>}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}
      {actionInfo && <div className='alert alert-success'>{actionInfo}</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>Name</th>
            <th>Segments</th>
            <th>Linked promotion</th>
            <th className='text-end'>Audience</th>
            <th className='text-end'>Budget</th>
            <th>Window</th>
            <th>Status</th>
            <th className='text-end'>Actions</th>
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={8} className='text-center p-5'>
                <span className='spinner-border text-primary' role='status' />
              </td>
            </tr>
          ) : list.length === 0 ? (
            <tr>
              <td colSpan={8} className='text-center text-muted py-5'>
                No campaigns defined.
              </td>
            </tr>
          ) : (
            list.map(c => {
              const statusKey = (c.status ?? '').toLowerCase();
              const mautic = mauticSegmentUrl(c.id);
              return (
                <tr key={c.id}>
                  <td>
                    {c.name || `#${c.id}`}
                    <div className='small text-muted'>#{c.id}</div>
                  </td>
                  <td style={{ maxWidth: 200 }}>
                    {(c.targetSegments ?? []).map(s => (
                      <Tag key={s} tag='info'>
                        {s}
                      </Tag>
                    ))}
                    {(c.targetSegments ?? []).length === 0 && '—'}
                  </td>
                  <td>{c.linkedPromotionType ? `${c.linkedPromotionType} #${c.linkedPromotionId ?? '?'}` : '—'}</td>
                  <td className='text-end'>
                    {c.assignedCount ?? 0}
                    {c.maxAudience != null && ` / ${c.maxAudience}`}
                  </td>
                  <td className='text-end'>
                    {c.spentBudget ?? 0}
                    {c.maxSpend != null && ` / ${c.maxSpend}`}
                  </td>
                  <td className='small'>{window_(c)}</td>
                  <td>
                    <Tag tag={STATUS_TAG[statusKey] ?? 'info'}>{c.status ?? 'unknown'}</Tag>
                  </td>
                  <td className='text-end' style={{ whiteSpace: 'nowrap' }}>
                    {statusKey === 'draft' && (
                      <button className='btn btn-sm btn-outline-success me-1' disabled={busy} onClick={() => run('activate', c)}>
                        Activate
                      </button>
                    )}
                    {statusKey === 'active' && (
                      <button className='btn btn-sm btn-outline-primary me-1' disabled={busy} onClick={() => run('evaluate', c)}>
                        Evaluate
                      </button>
                    )}
                    {mautic && (
                      <a className='btn btn-sm btn-outline-secondary' href={mautic} target='_blank' rel='noreferrer'>
                        Segment in Mautic ↗
                      </a>
                    )}
                  </td>
                </tr>
              );
            })
          )}
        </tbody>
      </table>
    </div>
  );
};

export default CampaignList;
