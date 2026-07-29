import {
  useDeleteCategoryMarginMutation,
  usePutCategoryMarginMutation,
  useSimulateCategoryMarginQuery,
} from 'app/shared/reducers/private/services/insightApi';
import { errnoMessage } from 'app/views/adminViews/adminModule/_shared/crudUi';
import { fmtInt, fmtMoney } from 'app/views/adminViews/adminModule/Insight/insightFormat';
import * as React from 'react';
import { Button, Modal } from 'react-bootstrap';

// Wave 14: per-category margin tuning. The debounced simulate preview is a
// PURE READ over costed goods (no price mutation); Apply stores the override
// (bounds 1.05–3.0), which takes effect at the NEXT NIGHTLY reprice — never
// immediately. Global default margin stays untouched.

const MARGIN_MIN = 1.05;
const MARGIN_MAX = 3.0;

interface Props {
  categoryId: number;
  categoryName?: string;
  /** Existing override margin, if any — seeds the input. */
  currentOverride?: number;
  onClose: () => void;
}

const MarginTuningDialog: React.FC<Props> = ({ categoryId, categoryName, currentOverride, onClose }) => {
  const [input, setInput] = React.useState<string>(currentOverride != null ? String(currentOverride) : '1.25');
  const [debounced, setDebounced] = React.useState<number | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [done, setDone] = React.useState<string | null>(null);

  const margin = Number(input);
  const valid = Number.isFinite(margin) && margin >= MARGIN_MIN && margin <= MARGIN_MAX;

  React.useEffect(() => {
    if (!valid) return undefined;
    const t = setTimeout(() => setDebounced(margin), 400);
    return () => clearTimeout(t);
  }, [margin, valid]);

  const { data: sim, isFetching: simulating, isError: simError } = useSimulateCategoryMarginQuery(
    { categoryId, margin: debounced ?? 0 },
    { skip: debounced == null }
  );

  const [put, { isLoading: applying }] = usePutCategoryMarginMutation();
  const [remove, { isLoading: removing }] = useDeleteCategoryMarginMutation();

  const label = categoryName || `category #${categoryId}`;

  const runMutation = async (action: () => ReturnType<typeof put> | ReturnType<typeof remove>, successMsg: string) => {
    setError(null);
    const res = await action();
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
    setDone(successMsg);
  };

  const onApply = () => {
    if (!valid) {
      setError(`Margin must be between ${MARGIN_MIN} and ${MARGIN_MAX}.`);
      return;
    }
    if (!window.confirm(`Apply margin ×${margin} to ${label}? Its goods reprice at the next nightly cycle — nothing changes immediately.`)) return;
    void runMutation(() => put({ categoryId, margin }), `Override ×${margin} saved. Prices update at the next nightly reprice.`);
  };

  const onRemove = () => {
    if (!window.confirm(`Remove the margin override for ${label}? Goods return to the global default at the next nightly reprice.`)) return;
    void runMutation(() => remove({ categoryId }), 'Override removed. The global default applies again from the next nightly reprice.');
  };

  return (
    <Modal show onHide={onClose} centered>
      <Modal.Header closeButton>
        <Modal.Title as='h5'>Margin — {label}</Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {done ? (
          <div className='alert alert-success mb-0'>{done}</div>
        ) : (
          <>
            {error && <div className='alert alert-danger'>{error}</div>}
            <div className='mb-3'>
              <label className='form-label'>
                Margin multiplier <span className='text-muted small'>(retail = CJ cost × margin; {MARGIN_MIN}–{MARGIN_MAX})</span>
              </label>
              <div className='d-flex align-items-center gap-2'>
                <input
                  type='range'
                  className='form-range flex-grow-1'
                  min={MARGIN_MIN}
                  max={MARGIN_MAX}
                  step={0.05}
                  value={valid ? margin : 1.25}
                  onChange={e => setInput(e.target.value)}
                  aria-label='Margin slider'
                />
                <input
                  type='number'
                  min={MARGIN_MIN}
                  max={MARGIN_MAX}
                  step='0.01'
                  className={`form-control${input !== '' && !valid ? ' is-invalid' : ''}`}
                  style={{ width: 100 }}
                  value={input}
                  onChange={e => setInput(e.target.value)}
                  aria-label='Margin value'
                />
              </div>
              {input !== '' && !valid && <div className='text-danger small mt-1'>Must be between {MARGIN_MIN} and {MARGIN_MAX}.</div>}
              {currentOverride != null && <div className='form-text'>Current override: ×{currentOverride}</div>}
            </div>

            {simError ? (
              <div className='alert alert-warning'>Simulation unavailable — the backend could not be reached.</div>
            ) : sim ? (
              <table className='table table-sm mb-2'>
                <thead>
                  <tr>
                    <th />
                    <th className='text-end'>Now {sim.currentMargin != null ? `(×${sim.currentMargin})` : ''}</th>
                    <th className='text-end'>At ×{sim.simulatedMargin ?? debounced}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td>Avg price</td>
                    <td className='text-end'>{fmtMoney(sim.avgPriceNow)}</td>
                    <td className='text-end'>
                      <strong>{fmtMoney(sim.avgPriceAt)}</strong>
                    </td>
                  </tr>
                  <tr>
                    <td>Potential profit</td>
                    <td className='text-end'>{fmtMoney(sim.potentialProfitNow)}</td>
                    <td className='text-end'>
                      <strong>{fmtMoney(sim.potentialProfitAt)}</strong>
                    </td>
                  </tr>
                </tbody>
              </table>
            ) : (
              <p className='text-muted small mb-2'>{simulating ? 'Simulating…' : 'Set a margin to preview its effect.'}</p>
            )}
            {sim && (
              <p className='text-muted small mb-0'>
                Over {fmtInt(sim.goodsCount)} costed goods{simulating ? ' · updating…' : ''}. Goods without a captured CJ cost are excluded
                and keep their price until costed.
              </p>
            )}
          </>
        )}
      </Modal.Body>
      <Modal.Footer>
        <Button variant='secondary' onClick={onClose}>
          {done ? 'Close' : 'Cancel'}
        </Button>
        {!done && currentOverride != null && (
          <Button variant='outline-danger' disabled={removing || applying} onClick={onRemove}>
            {removing ? 'Removing…' : 'Remove override'}
          </Button>
        )}
        {!done && (
          <Button variant='primary' disabled={!valid || applying || removing} onClick={onApply}>
            {applying ? 'Applying…' : 'Apply to this category'}
          </Button>
        )}
      </Modal.Footer>
    </Modal>
  );
};

export default MarginTuningDialog;
