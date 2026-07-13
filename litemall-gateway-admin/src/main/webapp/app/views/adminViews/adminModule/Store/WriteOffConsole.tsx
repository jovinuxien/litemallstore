import {
  IWriteoffPreview,
  useCommitWriteoffMutation,
  useLazyPreviewWriteoffQuery,
} from 'app/shared/reducers/private/services/adminStoreApi';
import * as React from 'react';

// Pickup write-off (核销) console — scanner-first workflow:
//   1. A single large autofocused code input; a USB barcode scanner types the
//      code and sends Enter, which submits the PREVIEW
//      (GET /srv/private/admin/order/writeoff?verifyCode=).
//   2. The preview card shows the order (sn, pickup name/mobile, store, goods,
//      amount); the operator presses Confirm, which COMMITS
//      (POST /srv/private/admin/order/writeoff {verifyCode}) and completes the
//      pickup (order moves to delivered/picked-up).
//   3. On success the input clears and refocuses for the next scan; on failure
//      the backend's exact errmsg is shown (three distinct business errors:
//      unknown code / already verified / wrong order state) and the code stays
//      editable.
// A small in-memory list keeps the write-offs of this browser session.

interface HistoryEntry {
  verifyCode: string;
  orderSn?: string;
  pickupName?: string;
  actualPrice?: number;
  at: string;
}

const HISTORY_LIMIT = 10;

const money = (v?: number): string => (v == null ? '—' : `¥${Number(v).toFixed(2)}`);

const WriteOffConsole: React.FC = () => {
  const inputRef = React.useRef<HTMLInputElement>(null);

  const [code, setCode] = React.useState('');
  // The code the current preview belongs to — commit uses this, not the live
  // input value, so edits after a preview can't commit an unpreviewed code.
  const [previewedCode, setPreviewedCode] = React.useState<string | null>(null);
  const [preview, setPreview] = React.useState<IWriteoffPreview | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [success, setSuccess] = React.useState<string | null>(null);
  const [history, setHistory] = React.useState<HistoryEntry[]>([]);

  const [triggerPreview, { isFetching: previewing }] = useLazyPreviewWriteoffQuery();
  const [commitWriteoff, { isLoading: committing }] = useCommitWriteoffMutation();

  React.useEffect(() => {
    inputRef.current?.focus();
  }, []);

  const reset = (keepSuccess?: string) => {
    setPreview(null);
    setPreviewedCode(null);
    setError(null);
    setSuccess(keepSuccess ?? null);
    setCode('');
    inputRef.current?.focus();
  };

  const onScan = async (e: React.FormEvent) => {
    e.preventDefault();
    const verifyCode = code.trim();
    if (!verifyCode || previewing || committing) return;
    setError(null);
    setSuccess(null);
    setPreview(null);
    setPreviewedCode(null);
    try {
      const env = await triggerPreview(verifyCode).unwrap();
      if (env.errno === 0 && env.data) {
        setPreview(env.data);
        setPreviewedCode(verifyCode);
      } else {
        // Unknown code / already verified / wrong order state — surface the
        // backend's exact wording. The code stays in the input for correction.
        setError(env.errmsg || `Lookup failed (errno ${env.errno})`);
      }
    } catch {
      setError('Lookup failed — the order service did not respond.');
    }
    inputRef.current?.focus();
  };

  const onConfirm = async () => {
    if (!previewedCode || committing) return;
    setError(null);
    try {
      const env = await commitWriteoff({ verifyCode: previewedCode }).unwrap();
      if (env.errno === 0) {
        setHistory(prev =>
          [
            {
              verifyCode: previewedCode,
              orderSn: preview?.orderSn,
              pickupName: preview?.pickupName,
              actualPrice: preview?.actualPrice,
              at: new Date().toLocaleTimeString(),
            },
            ...prev,
          ].slice(0, HISTORY_LIMIT),
        );
        reset(`Order ${preview?.orderSn ?? previewedCode} written off — ready for the next scan.`);
      } else {
        // Distinct backend failures (unknown code / already verified / wrong
        // order state) each carry their own errmsg — show it verbatim.
        setError(env.errmsg || `Write-off failed (errno ${env.errno})`);
        inputRef.current?.focus();
      }
    } catch {
      setError('Write-off failed — the order service did not respond.');
      inputRef.current?.focus();
    }
  };

  const busy = previewing || committing;

  return (
    <div className='app-container'>
      <h5 className='mb-1'>Pickup write-off console</h5>
      <p className='text-muted small mb-3'>
        Scan or type a pickup verification code and press Enter to preview the order, then confirm to complete the pickup.
      </p>

      <form className='filter-container' onSubmit={onScan}>
        <input
          ref={inputRef}
          className='form-control filter-item'
          style={{ maxWidth: 420, fontSize: '1.4rem', padding: '0.6rem 0.9rem' }}
          placeholder='Verification code'
          value={code}
          onChange={e => setCode(e.target.value)}
          autoFocus
          autoComplete='off'
          spellCheck={false}
          aria-label='Pickup verification code'
        />
        <button className='btn btn-primary filter-item' type='submit' disabled={!code.trim() || busy}>
          {previewing ? 'Looking up…' : 'Look up'}
        </button>
        {preview && (
          <button className='btn btn-outline-secondary filter-item' type='button' disabled={busy} onClick={() => reset()}>
            Clear
          </button>
        )}
      </form>

      {success && <div className='alert alert-success'>{success}</div>}
      {error && (
        <div className='alert alert-danger' role='alert'>
          <strong>Write-off error:</strong> {error}
        </div>
      )}

      {preview && (
        <div className='card mb-3' style={{ maxWidth: 720 }}>
          <div className='card-header d-flex justify-content-between align-items-center'>
            <span>
              Order <strong>{preview.orderSn ?? `#${preview.id ?? '?'}`}</strong>
            </span>
            {(preview.orderStatusText || preview.orderStatus != null) && (
              <span className='el-tag el-tag--info'>{preview.orderStatusText ?? `status ${preview.orderStatus}`}</span>
            )}
          </div>
          <div className='card-body'>
            <div className='row mb-2'>
              <div className='col'>
                <div className='text-muted small'>Pickup by</div>
                <div>
                  {preview.pickupName ?? '—'}
                  {preview.pickupMobile && <span className='text-muted ms-2'>{preview.pickupMobile}</span>}
                </div>
              </div>
              <div className='col'>
                <div className='text-muted small'>Store</div>
                <div>{preview.storeName ?? '—'}</div>
              </div>
              <div className='col text-end'>
                <div className='text-muted small'>Amount</div>
                <div className='fw-bold'>{money(preview.actualPrice)}</div>
              </div>
            </div>

            {preview.goodsList.length > 0 && (
              <table className='el-table'>
                <thead>
                  <tr>
                    <th>Item</th>
                    <th className='text-end'>Qty</th>
                    <th className='text-end'>Price</th>
                  </tr>
                </thead>
                <tbody>
                  {preview.goodsList.map((g, i) => (
                    <tr key={i}>
                      <td>
                        <div className='d-flex align-items-center'>
                          {g.picUrl && <img src={g.picUrl} alt='' className='cell-thumb me-2' />}
                          <div>
                            {g.goodsName ?? '—'}
                            {g.specifications && g.specifications.length > 0 && (
                              <div className='text-muted small'>{g.specifications.join(' / ')}</div>
                            )}
                          </div>
                        </div>
                      </td>
                      <td className='text-end'>{g.number ?? '—'}</td>
                      <td className='text-end'>{money(g.price)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}

            <div className='mt-3 text-end'>
              <button className='btn btn-success btn-lg' type='button' disabled={busy} onClick={onConfirm}>
                {committing ? 'Confirming…' : 'Confirm pickup'}
              </button>
            </div>
          </div>
        </div>
      )}

      {history.length > 0 && (
        <div style={{ maxWidth: 720 }}>
          <h6 className='mb-2'>This session ({history.length})</h6>
          <table className='el-table'>
            <thead>
              <tr>
                <th>Time</th>
                <th>Order</th>
                <th>Pickup by</th>
                <th>Code</th>
                <th className='text-end'>Amount</th>
              </tr>
            </thead>
            <tbody>
              {history.map((h, i) => (
                <tr key={i}>
                  <td>{h.at}</td>
                  <td>{h.orderSn ?? '—'}</td>
                  <td>{h.pickupName ?? '—'}</td>
                  <td className='text-muted small'>{h.verifyCode}</td>
                  <td className='text-end'>{money(h.actualPrice)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default WriteOffConsole;
