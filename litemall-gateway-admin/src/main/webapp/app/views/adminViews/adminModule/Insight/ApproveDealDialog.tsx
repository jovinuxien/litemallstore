import { useApproveDealCandidateMutation } from 'app/shared/reducers/private/services/insightApi';
import { adminDealApi } from 'app/shared/reducers/private/services/adminDealApi';
import { errnoMessage } from 'app/views/adminViews/adminModule/_shared/crudUi';
import { fmtMoney } from 'app/views/adminViews/adminModule/Insight/insightFormat';
import { useAppDispatch } from 'app/config/store';
import * as React from 'react';
import { Button, Modal } from 'react-bootstrap';
import { Link } from 'react-router-dom';

// Wave 12: approve a proposed deal candidate — creates the flash deal via
// POST /insight/deal-candidates/{goodsId}/approve (the CJ-unparked path).
// The deal price must never go below the captured CJ cost: the backend
// enforces it, and the form pre-validates the same rule so the admin sees
// the problem before submitting. On success the created deal is visible in
// the existing Flash Deals panel — its RTK cache is invalidated here.

interface Props {
  goodsId: number;
  goodsName?: string;
  cost?: number | null;
  suggestedDealPrice?: number;
  defaultStock?: number;
  onClose: () => void;
}

const ApproveDealDialog: React.FC<Props> = ({ goodsId, goodsName, cost, suggestedDealPrice, defaultStock, onClose }) => {
  const dispatch = useAppDispatch();
  const [approve, { isLoading: saving }] = useApproveDealCandidateMutation();

  const [dealPrice, setDealPrice] = React.useState<string>(suggestedDealPrice != null ? String(suggestedDealPrice) : '');
  const [startTime, setStartTime] = React.useState('');
  const [stopTime, setStopTime] = React.useState('');
  const [stock, setStock] = React.useState<string>(defaultStock != null ? String(defaultStock) : '0');
  const [error, setError] = React.useState<string | null>(null);
  const [done, setDone] = React.useState(false);

  const priceNum = Number(dealPrice);
  const belowCost = cost != null && dealPrice !== '' && Number.isFinite(priceNum) && priceNum < cost;

  const validate = (): string | null => {
    if (!Number.isFinite(priceNum) || priceNum <= 0) return 'Enter a deal price greater than 0.';
    if (belowCost) return `Deal price is below the CJ cost (${fmtMoney(cost)}) — not allowed.`;
    if (!startTime || !stopTime) return 'Set the deal start and stop time.';
    if (stopTime <= startTime) return 'Stop time must be after the start time.';
    const stockNum = Number(stock);
    if (!Number.isInteger(stockNum) || stockNum < 0) return 'Stock must be 0 (uncapped) or a positive integer.';
    return null;
  };

  const onSubmit = async () => {
    const invalid = validate();
    setError(invalid);
    if (invalid) return;
    // Promotion/goods datetimes are strict ISO LocalDateTime; pad the
    // datetime-local 'YYYY-MM-DDTHH:mm' value.
    const iso = (v: string) => (v.length === 16 ? `${v}:00` : v);
    const res = await approve({ goodsId, dealPrice: priceNum, startTime: iso(startTime), stopTime: iso(stopTime), stock: Number(stock) });
    if ('error' in res && res.error) {
      const status = (res.error as { status?: number | string })?.status;
      setError(`Request failed${status ? ` (${status})` : ''}.`);
      return;
    }
    const msg = errnoMessage(res.data);
    if (msg) {
      setError(msg);
      return;
    }
    // The new deal lives in the Flash Deals panel — refresh its cache too.
    dispatch(adminDealApi.util.invalidateTags(['Deal']));
    setDone(true);
  };

  return (
    <Modal show onHide={onClose} centered>
      <Modal.Header closeButton>
        <Modal.Title as='h5'>Approve deal — {goodsName || `goods #${goodsId}`}</Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {done ? (
          <div className='alert alert-success mb-0'>
            Flash deal created. See it in the{' '}
            <Link to='/admin/promotion/deal' onClick={onClose}>
              Flash Deals panel
            </Link>
            .
          </div>
        ) : (
          <>
            {error && <div className='alert alert-danger'>{error}</div>}
            <div className='mb-2'>
              <label className='form-label'>
                Deal price {cost != null ? <span className='text-muted small'>(CJ cost {fmtMoney(cost)})</span> : <span className='text-muted small'>(cost not captured yet)</span>}
              </label>
              <input
                type='number'
                min={0}
                step='0.01'
                className={`form-control${belowCost ? ' is-invalid' : ''}`}
                value={dealPrice}
                onChange={e => setDealPrice(e.target.value)}
              />
              {belowCost && <div className='invalid-feedback'>Below cost — the backend will refuse this.</div>}
            </div>
            <div className='row'>
              <div className='col mb-2'>
                <label className='form-label'>Start</label>
                <input type='datetime-local' className='form-control' value={startTime} onChange={e => setStartTime(e.target.value)} />
              </div>
              <div className='col mb-2'>
                <label className='form-label'>Stop</label>
                <input type='datetime-local' className='form-control' value={stopTime} onChange={e => setStopTime(e.target.value)} />
              </div>
            </div>
            <div className='mb-2'>
              <label className='form-label'>
                Deal stock <span className='text-muted small'>(0 = uncapped)</span>
              </label>
              <input type='number' min={0} step={1} className='form-control' value={stock} onChange={e => setStock(e.target.value)} />
            </div>
          </>
        )}
      </Modal.Body>
      <Modal.Footer>
        <Button variant='secondary' onClick={onClose}>
          {done ? 'Close' : 'Cancel'}
        </Button>
        {!done && (
          <Button variant='primary' disabled={saving || belowCost} onClick={onSubmit}>
            {saving ? 'Approving…' : 'Approve deal'}
          </Button>
        )}
      </Modal.Footer>
    </Modal>
  );
};

export default ApproveDealDialog;
