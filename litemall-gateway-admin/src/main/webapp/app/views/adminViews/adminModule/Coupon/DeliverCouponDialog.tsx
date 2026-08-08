import { ICoupon } from 'app/shared/model/admin/promotion-system.model';
import {
  ICouponDeliverResult,
  promotionOpMessage,
  toDeliverResult,
  useDeliverCouponMutation,
} from 'app/shared/reducers/private/services/adminPromotionApi';
import {
  SegmentInputs,
  deliverResultText,
  segmentClientError,
  segmentFromInputs,
  segmentLabel,
} from './couponDeliveryFormat';
import * as React from 'react';
import { Button, Modal } from 'react-bootstrap';

// Wave 22: RFM-targeted coupon delivery. Preview computes the matched count
// with ZERO side effects; Deliver grants through the existing coupon grant
// path (idempotent per user via the claim limit). Deliver stays disabled
// until the CURRENT inputs have been previewed, so the admin always sees the
// audience size before any grant. Typed refusals (expired/inactive coupon)
// and any other server message are surfaced VERBATIM.

interface Props {
  coupon: ICoupon;
  onClose: () => void;
}

const EMPTY_INPUTS: SegmentInputs = { recencyDays: '', minFrequency: '', minMonetary: '' };

const DeliverCouponDialog: React.FC<Props> = ({ coupon, onClose }) => {
  const [inputs, setInputs] = React.useState<SegmentInputs>(EMPTY_INPUTS);
  const [error, setError] = React.useState<string | null>(null);
  const [preview, setPreview] = React.useState<ICouponDeliverResult | null>(null);
  // The inputs the current preview belongs to — editing any field stales it.
  const [previewedFor, setPreviewedFor] = React.useState<string | null>(null);
  const [delivered, setDelivered] = React.useState<ICouponDeliverResult | null>(null);

  const [deliver, { isLoading: busy }] = useDeliverCouponMutation();

  const segment = segmentFromInputs(inputs);
  const inputsKey = JSON.stringify(segment);
  const previewFresh = preview != null && previewedFor === inputsKey;
  const label = coupon.name || `#${coupon.id}`;

  const set = (patch: Partial<SegmentInputs>) => {
    setInputs(prev => ({ ...prev, ...patch }));
    setError(null);
  };

  const run = async (isPreview: boolean): Promise<ICouponDeliverResult | null> => {
    setError(null);
    const clientError = segmentClientError(inputs);
    if (clientError) {
      setError(clientError);
      return null;
    }
    const res = await deliver({ couponId: coupon.id as number, ...segment, ...(isPreview ? { preview: true } : {}) });
    const msg = promotionOpMessage(res);
    if (msg) {
      setError(msg);
      return null;
    }
    return toDeliverResult((res as { data?: unknown }).data);
  };

  const onPreview = async () => {
    const r = await run(true);
    if (r) {
      setPreview(r);
      setPreviewedFor(inputsKey);
    }
  };

  const onDeliver = async () => {
    const matched = preview?.matched ?? 0;
    if (!window.confirm(`Grant coupon "${label}" to the ${matched} matched user${matched === 1 ? '' : 's'} (${segmentLabel(segment)})?`)) return;
    const r = await run(false);
    if (r) setDelivered(r);
  };

  return (
    <Modal show onHide={onClose} centered>
      <Modal.Header closeButton>
        <Modal.Title as='h5'>Deliver to segment — {label}</Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {delivered ? (
          <div className='alert alert-success mb-0'>
            Delivered: {deliverResultText(delivered, false)}
            <div className='text-muted small mt-1'>Skipped users already held this coupon or hit its claim limit.</div>
          </div>
        ) : (
          <>
            <p className='text-muted small'>
              Every criterion is optional — leave all empty to target every customer. Preview shows the audience size before anything is
              granted; delivery is idempotent per user via the coupon&apos;s claim limit.
            </p>
            {error && <div className='alert alert-danger'>{error}</div>}
            <div className='mb-3'>
              <label className='form-label'>Recency — bought within N days</label>
              <input
                type='number'
                min={1}
                className='form-control'
                placeholder='e.g. 30 (empty = any time)'
                value={inputs.recencyDays}
                onChange={e => set({ recencyDays: e.target.value })}
              />
            </div>
            <div className='mb-3'>
              <label className='form-label'>Frequency — at least N paid orders</label>
              <input
                type='number'
                min={1}
                className='form-control'
                placeholder='e.g. 2 (empty = any)'
                value={inputs.minFrequency}
                onChange={e => set({ minFrequency: e.target.value })}
              />
            </div>
            <div className='mb-3'>
              <label className='form-label'>Monetary — spent at least $N</label>
              <input
                type='number'
                min={0}
                step='0.01'
                className='form-control'
                placeholder='e.g. 50 (empty = any)'
                value={inputs.minMonetary}
                onChange={e => set({ minMonetary: e.target.value })}
              />
            </div>
            <div className='text-muted small'>Segment: {segmentLabel(segment)}</div>
            {preview && (
              <div className={`alert ${previewFresh ? 'alert-info' : 'alert-warning'} mt-3 mb-0`}>
                {deliverResultText(preview, true)}
                {!previewFresh && ' Inputs changed — preview again before delivering.'}
              </div>
            )}
          </>
        )}
      </Modal.Body>
      <Modal.Footer>
        <Button variant='outline-secondary' onClick={onClose} disabled={busy}>
          Close
        </Button>
        {!delivered && (
          <>
            <Button variant='outline-primary' onClick={() => void onPreview()} disabled={busy}>
              {busy ? 'Working…' : 'Preview'}
            </Button>
            <Button variant='primary' onClick={() => void onDeliver()} disabled={busy || !previewFresh} title={previewFresh ? undefined : 'Preview the segment first'}>
              Deliver
            </Button>
          </>
        )}
      </Modal.Footer>
    </Modal>
  );
};

export default DeliverCouponDialog;
