import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Form, Spinner } from 'react-bootstrap';

import { CellGroup } from 'app/components/commonComponents/storefront';
import { orderApi } from 'app/shared/api';
import { useTranslation } from 'app/i18n';
import { IDispute, IDisputeContext } from 'app/shared/model/order/order.model';
import { money } from 'app/shared/util/money';

/**
 * "Report a problem" panel for a DROPSHIP (source='cj') order: lists the order's CJ
 * disputes (status lazily refreshed from CJ by the backend) and, when none is open,
 * offers a form — pick the affected items, a CJ reason, refund-or-reissue, and a
 * description. All CJ-proxied calls are slow (2-6s); buttons show progress.
 */
const DisputePanel: React.FC<{ orderId: number }> = ({ orderId }) => {
  const { t } = useTranslation('order');
  const [disputes, setDisputes] = useState<IDispute[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [context, setContext] = useState<IDisputeContext | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Form state
  const [selected, setSelected] = useState<Record<string, number>>({}); // lineItemId -> qty
  const [reasonId, setReasonId] = useState<number | ''>('');
  const [expectType, setExpectType] = useState<'REFUND' | 'REISSUE'>('REFUND');
  const [message, setMessage] = useState('');

  const refresh = useCallback(async () => {
    try {
      setDisputes((await orderApi.disputeList(orderId)) ?? []);
    } catch {
      /* dispute history is best-effort; the panel still renders */
    } finally {
      setLoaded(true);
    }
  }, [orderId]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const hasOpenDispute = disputes.some(d => d.open);

  const startForm = async () => {
    setBusy(true);
    setError(null);
    try {
      const ctx = await orderApi.disputeContext(orderId);
      setContext(ctx);
      setSelected(Object.fromEntries((ctx.lines ?? []).map(l => [l.lineItemId, l.maxQuantity])));
      setReasonId(ctx.reasons?.[0]?.id ?? '');
      setExpectType(ctx.refundAllowed || !ctx.reissueAllowed ? 'REFUND' : 'REISSUE');
      setFormOpen(true);
    } catch (e) {
      setError((e as { message?: string })?.message ?? t('dispute.loadFailed'));
    } finally {
      setBusy(false);
    }
  };

  const submit = async () => {
    if (!context || reasonId === '') return;
    const lines = Object.entries(selected)
      .filter(([, qty]) => qty > 0)
      .map(([lineItemId, quantity]) => ({ lineItemId, quantity }));
    if (!lines.length) {
      setError(t('dispute.pickItem'));
      return;
    }
    if (!message.trim()) {
      setError(t('dispute.describeRequired'));
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await orderApi.disputeOpen(orderId, {
        reasonId: Number(reasonId),
        reasonName: context.reasons.find(r => r.id === reasonId)?.name,
        expectType,
        message: message.trim(),
        lines,
      });
      setFormOpen(false);
      setMessage('');
      await refresh();
    } catch (e) {
      setError((e as { message?: string })?.message ?? t('dispute.openFailed'));
    } finally {
      setBusy(false);
    }
  };

  const cancelDispute = async (disputeId?: number) => {
    if (disputeId == null) return;
    setBusy(true);
    setError(null);
    try {
      await orderApi.disputeCancel(orderId, disputeId);
      await refresh();
    } catch (e) {
      setError((e as { message?: string })?.message ?? t('dispute.cancelFailed'));
    } finally {
      setBusy(false);
    }
  };

  if (!loaded) return null;

  return (
    <CellGroup title={t('dispute.title')}>
      <div className='p-3'>
        {error && <Alert variant='danger'>{error}</Alert>}

        {/* Existing disputes */}
        {disputes.map(d => (
          <div key={d.id} className='border rounded p-2 mb-2 d-flex justify-content-between align-items-start'>
            <div>
              <div className='fw-semibold'>
                {d.reasonName ?? t('dispute.dispute')} <span className='text-muted'>{d.expectType === 'REISSUE' ? t('dispute.wantsReissue') : t('dispute.wantsRefund')}</span>
              </div>
              <div className='small text-muted'>{d.message}</div>
              <div className='small mt-1'>
                {d.cancelled ? (
                  <span className='badge text-bg-secondary'>{t('dispute.cancelled')}</span>
                ) : d.resolution ? (
                  <span className={`badge ${d.resolution === 'REJECTED' ? 'text-bg-danger' : 'text-bg-success'}`}>
                    {d.resolution === 'REFUND' && `${t('dispute.refunded')}${d.refundAmountUsd != null ? ` ${money(d.refundAmountUsd)}` : ''}`}
                    {d.resolution === 'REISSUE' && `${t('dispute.reissued')}${d.resendOrderCode ? ` (${d.resendOrderCode})` : ''}`}
                    {d.resolution === 'REJECTED' && t('dispute.rejected')}
                  </span>
                ) : (
                  <span className='badge text-bg-info'>{d.status ?? t('dispute.processing')}</span>
                )}
              </div>
            </div>
            {d.open && (
              <button type='button' className='btn btn-lm-outline btn-sm' disabled={busy} onClick={() => cancelDispute(d.id)}>
                {t('dispute.withdraw')}
              </button>
            )}
          </div>
        ))}

        {/* Open a new dispute */}
        {!hasOpenDispute && !formOpen && (
          <button type='button' className='btn btn-lm-outline btn-sm' disabled={busy} onClick={startForm}>
            {busy ? <Spinner size='sm' animation='border' /> : <><i className='bi bi-exclamation-circle me-1' />{t('dispute.report')}</>}
          </button>
        )}

        {formOpen && context && (
          <div className='mt-2'>
            <Form.Label className='small text-muted mb-1'>{t('dispute.affectedItems')}</Form.Label>
            {context.lines.map(l => (
              <div key={l.lineItemId} className='d-flex align-items-center gap-2 mb-1'>
                <Form.Check
                  type='checkbox'
                  id={`dl-${l.lineItemId}`}
                  checked={(selected[l.lineItemId] ?? 0) > 0}
                  onChange={e => setSelected(prev => ({ ...prev, [l.lineItemId]: e.target.checked ? l.maxQuantity : 0 }))}
                  label={t('dispute.lineLabel', { name: l.productName ?? t('dispute.item'), price: l.unitPriceUsd != null ? money(l.unitPriceUsd) : '?', max: l.maxQuantity })}
                />
                {(selected[l.lineItemId] ?? 0) > 0 && l.maxQuantity > 1 && (
                  <Form.Select
                    size='sm'
                    style={{ width: 70 }}
                    value={selected[l.lineItemId]}
                    onChange={e => setSelected(prev => ({ ...prev, [l.lineItemId]: Number(e.target.value) }))}
                  >
                    {Array.from({ length: l.maxQuantity }, (_, i) => i + 1).map(q => (
                      <option key={q} value={q}>
                        {q}
                      </option>
                    ))}
                  </Form.Select>
                )}
              </div>
            ))}

            <Form.Label className='small text-muted mb-1 mt-2'>{t('dispute.reason')}</Form.Label>
            <Form.Select size='sm' value={reasonId} onChange={e => setReasonId(e.target.value ? Number(e.target.value) : '')}>
              {context.reasons.map(r => (
                <option key={r.id} value={r.id}>
                  {r.name}
                </option>
              ))}
            </Form.Select>

            <Form.Label className='small text-muted mb-1 mt-2'>{t('dispute.whatShouldHappen')}</Form.Label>
            <div>
              <Form.Check
                inline
                type='radio'
                id='exp-refund'
                label={t('dispute.refund')}
                disabled={!context.refundAllowed}
                checked={expectType === 'REFUND'}
                onChange={() => setExpectType('REFUND')}
              />
              <Form.Check
                inline
                type='radio'
                id='exp-reissue'
                label={t('dispute.sendAgain')}
                disabled={!context.reissueAllowed}
                checked={expectType === 'REISSUE'}
                onChange={() => setExpectType('REISSUE')}
              />
            </div>

            <Form.Label className='small text-muted mb-1 mt-2'>{t('dispute.describe')}</Form.Label>
            <Form.Control
              as='textarea'
              rows={3}
              maxLength={500}
              value={message}
              placeholder={t('dispute.describePlaceholder')}
              onChange={e => setMessage(e.target.value)}
            />

            <div className='d-flex gap-2 mt-2 justify-content-end'>
              <button type='button' className='btn btn-lm-outline btn-sm' disabled={busy} onClick={() => setFormOpen(false)}>
                {t('dispute.close')}
              </button>
              <button type='button' className='btn btn-lm-primary btn-sm' disabled={busy} onClick={submit}>
                {busy ? <Spinner size='sm' animation='border' /> : t('dispute.submit')}
              </button>
            </div>
          </div>
        )}
      </div>
    </CellGroup>
  );
};

export default DisputePanel;
