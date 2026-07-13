import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Form, Spinner } from 'react-bootstrap';

import { Cell, CellGroup } from 'app/components/commonComponents/storefront';
import ImageUploader from 'app/components/commonComponents/ImageUploader';
import { isMissingEndpoint, orderApi } from 'app/shared/api';
import { IAftersale } from 'app/shared/model/order/order.model';

const TYPE_LABELS: Record<number, string> = {
  0: 'Refund (goods not received)',
  1: 'Refund only (goods received)',
  2: 'Return and refund',
};

/**
 * Aftersale / RMA on the order detail (Wave-2 vertical, live on master —
 * litemall-order/docs/aftersale-vertical.md): existing applications with
 * status + cancel-while-requested, and an apply form (type, reason, optional
 * amount + comment, photos via ImageUploader → /srv/storage/upload). errno 730
 * (rule violation) surfaces inline. Endpoint absent → panel hides itself.
 */
const AftersalePanel: React.FC<{ orderId: number | string; canApply: boolean; onChanged?: () => void }> = ({
  orderId,
  canApply,
  onChanged,
}) => {
  const [applications, setApplications] = useState<IAftersale[]>([]);
  const [hidden, setHidden] = useState(false);
  const [showForm, setShowForm] = useState(false);
  const [type, setType] = useState(1);
  const [reason, setReason] = useState('');
  const [amount, setAmount] = useState('');
  const [comment, setComment] = useState('');
  const [pictures, setPictures] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(() => {
    orderApi
      .aftersaleList(orderId)
      .then(list => setApplications(list ?? []))
      .catch(e => {
        if (isMissingEndpoint(e)) setHidden(true);
      });
  }, [orderId]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const hasOpen = applications.some(a => a.status === 1 || a.status === 2);

  if (hidden || (!canApply && applications.length === 0)) return null;

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const amt = amount.trim() === '' ? undefined : Number(amount);
      await orderApi.aftersaleApply(orderId, {
        type,
        reason,
        amount: amt != null && Number.isFinite(amt) ? amt : undefined,
        pictures: pictures.length > 0 ? pictures : undefined,
        comment: comment || undefined,
      });
      setShowForm(false);
      setReason('');
      setAmount('');
      setComment('');
      setPictures([]);
      refresh();
      onChanged?.();
    } catch (err) {
      setError((err as Error)?.message || 'Could not submit the request.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <CellGroup title='After-sales'>
      {applications.map(a => (
        <Cell key={a.id} title={`${TYPE_LABELS[a.type ?? 1] ?? 'After-sales'} · ${a.statusText ?? ''}`}>
          <div className='small text-muted'>
            {a.reason}
            {a.amount != null && <> · requested ${Number(a.amount).toFixed(2)}</>}
            {a.addTime && <> · {a.addTime}</>}
          </div>
          {a.status === 1 && a.id != null && (
            <Button
              size='sm'
              variant='outline-secondary'
              className='mt-1'
              onClick={() => orderApi.aftersaleCancel(orderId, a.id as number).then(refresh)}
            >
              Cancel request
            </Button>
          )}
        </Cell>
      ))}

      {canApply && !hasOpen && !showForm && (
        <div className='p-3'>
          <Button size='sm' variant='outline-secondary' onClick={() => setShowForm(true)}>
            <i className='bi bi-arrow-counterclockwise me-1' />
            Request refund / return
          </Button>
        </div>
      )}

      {showForm && (
        <Form onSubmit={submit} className='p-3 pt-2'>
          {error && <Alert variant='danger'>{error}</Alert>}
          <Form.Group className='mb-2'>
            <Form.Label>Type</Form.Label>
            <Form.Select value={type} onChange={e => setType(Number(e.target.value))}>
              {Object.entries(TYPE_LABELS).map(([v, label]) => (
                <option key={v} value={v}>
                  {label}
                </option>
              ))}
            </Form.Select>
          </Form.Group>
          <Form.Group className='mb-2'>
            <Form.Label>Reason *</Form.Label>
            <Form.Control value={reason} onChange={e => setReason(e.target.value)} required />
          </Form.Group>
          <Form.Group className='mb-2'>
            <Form.Label>Amount (blank = full refund)</Form.Label>
            <Form.Control type='number' min='0' step='0.01' value={amount} onChange={e => setAmount(e.target.value)} />
          </Form.Group>
          <Form.Group className='mb-2'>
            <Form.Label>Photos</Form.Label>
            <ImageUploader value={pictures} onChange={setPictures} max={5} disabled={submitting} />
          </Form.Group>
          <Form.Group className='mb-3'>
            <Form.Label>Note</Form.Label>
            <Form.Control as='textarea' rows={2} value={comment} onChange={e => setComment(e.target.value)} />
          </Form.Group>
          <div className='d-flex gap-2'>
            <Button type='submit' size='sm' variant='primary' disabled={submitting || !reason}>
              {submitting ? <Spinner animation='border' size='sm' /> : 'Submit request'}
            </Button>
            <Button size='sm' variant='outline-secondary' onClick={() => setShowForm(false)} disabled={submitting}>
              Cancel
            </Button>
          </div>
        </Form>
      )}
    </CellGroup>
  );
};

export default AftersalePanel;
