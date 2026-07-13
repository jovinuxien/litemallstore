import {
  IFreightFreeRule,
  IFreightRegionRow,
  IFreightTemplateDetail,
  useCreateFreightTemplateMutation,
  useFreightTemplateDetailQuery,
  useLazyPreviewFreightQuery,
  useUpdateFreightTemplateMutation,
} from 'app/shared/reducers/private/services/adminFreightApi';
import { errnoMessage } from 'app/views/adminViews/adminModule/_shared/crudUi';
import { APPOINT_LABELS } from 'app/views/adminViews/adminModule/Freight/FreightTemplateList';
import * as React from 'react';
import { useNavigate, useParams } from 'react-router-dom';

// Create / edit a freight template: name + appoint mode + sort, a dynamic
// REGION ROW table (crmeb first/continue formula, countryCode '*' = any) and a
// dynamic FREE RULE table (free when qty >= number OR amount >= price), plus a
// dry-run PREVIEW widget against /srv/private/admin/freight/preview.
// All DTO field mapping lives in adminFreightApi.ts — this form only edits the
// normalised shape. Envelope errors (errno != 0) surface inline.

interface RegionRowEdit {
  id?: number;
  countryCode: string;
  provinceName: string;
  first: string;
  firstPrice: string;
  continue: string;
  continuePrice: string;
}

interface FreeRuleEdit {
  id?: number;
  countryCode: string;
  provinceName: string;
  number: string;
  price: string;
}

const NEW_REGION: RegionRowEdit = { countryCode: '*', provinceName: '', first: '1', firstPrice: '', continue: '1', continuePrice: '' };
const NEW_FREE_RULE: FreeRuleEdit = { countryCode: '*', provinceName: '', number: '', price: '' };

const str = (v: unknown): string => (v == null ? '' : String(v));

// ----- Preview widget ---------------------------------------------------------

const FreightPreviewWidget: React.FC<{ tempId: number }> = ({ tempId }) => {
  const [countryCode, setCountryCode] = React.useState('*');
  const [province, setProvince] = React.useState('');
  const [quantity, setQuantity] = React.useState('1');
  const [weight, setWeight] = React.useState('');
  const [trigger, { data, isFetching, isError }] = useLazyPreviewFreightQuery();

  const onPreview = () => {
    trigger({
      tempId,
      countryCode: countryCode.trim() || '*',
      province: province.trim() || undefined,
      quantity: quantity.trim() || '1',
      weight: weight.trim() || undefined,
    });
  };

  const renderBreakdownLine = (line: Record<string, unknown>, i: number) => (
    <li key={i} className='small'>
      {Object.entries(line)
        .filter(([, v]) => v != null && typeof v !== 'object')
        .map(([k, v]) => `${k}: ${String(v)}`)
        .join(' · ') || JSON.stringify(line)}
    </li>
  );

  return (
    <div className='box-card mb-3'>
      <div className='box-card-header'>Preview quote (dry run)</div>
      <div className='box-card-body'>
        <div className='row g-2 align-items-end'>
          <div className='col-md-2'>
            <label className='form-label small mb-1'>Country code</label>
            <input className='form-control form-control-sm' placeholder='*' value={countryCode} onChange={e => setCountryCode(e.target.value)} />
          </div>
          <div className='col-md-3'>
            <label className='form-label small mb-1'>Province (optional)</label>
            <input className='form-control form-control-sm' value={province} onChange={e => setProvince(e.target.value)} />
          </div>
          <div className='col-md-2'>
            <label className='form-label small mb-1'>Quantity</label>
            <input className='form-control form-control-sm' type='number' min='1' value={quantity} onChange={e => setQuantity(e.target.value)} />
          </div>
          <div className='col-md-2'>
            <label className='form-label small mb-1'>Weight (optional)</label>
            <input className='form-control form-control-sm' type='number' step='0.01' min='0' value={weight} onChange={e => setWeight(e.target.value)} />
          </div>
          <div className='col-md-3'>
            <button type='button' className='btn btn-sm btn-outline-primary' disabled={isFetching} onClick={onPreview}>
              {isFetching ? 'Computing…' : 'Preview freight'}
            </button>
          </div>
        </div>

        {isError && <div className='alert alert-danger mt-2 mb-0 py-2'>Preview request failed.</div>}
        {data && data.errno !== 0 && <div className='alert alert-danger mt-2 mb-0 py-2'>{data.errmsg}</div>}
        {data && data.errno === 0 && (
          <div className='mt-2'>
            <div>
              <strong>Freight: {data.amount != null ? data.amount.toFixed(2) : '—'}</strong>
              {data.free != null && <span className='ms-2'>{data.free ? '(free shipping)' : ''}</span>}
              {data.source && <span className='text-muted ms-2 small'>source: {data.source}</span>}
            </div>
            {data.extras.length > 0 && (
              <div className='text-muted small'>{data.extras.map(([k, v]) => `${k}: ${v}`).join(' · ')}</div>
            )}
            {data.breakdown.length > 0 && <ul className='mb-0 mt-1 ps-3'>{data.breakdown.map(renderBreakdownLine)}</ul>}
          </div>
        )}
      </div>
    </div>
  );
};

// ----- Form ---------------------------------------------------------------

const FreightTemplateForm: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const isEdit = Boolean(id);
  const navigate = useNavigate();

  const { data: existing, isLoading: loading } = useFreightTemplateDetailQuery(id as string, { skip: !isEdit });
  const [createTemplate, { isLoading: creating }] = useCreateFreightTemplateMutation();
  const [updateTemplate, { isLoading: updating }] = useUpdateFreightTemplateMutation();

  const [name, setName] = React.useState('');
  const [appoint, setAppoint] = React.useState(0);
  const [sortOrder, setSortOrder] = React.useState('100');
  const [regions, setRegions] = React.useState<RegionRowEdit[]>([{ ...NEW_REGION }]);
  const [freeRules, setFreeRules] = React.useState<FreeRuleEdit[]>([]);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (!isEdit || !existing) return;
    setName(existing.template.name);
    setAppoint(existing.template.appoint);
    setSortOrder(str(existing.template.sortOrder ?? 100));
    setRegions(
      existing.regions.length > 0
        ? existing.regions.map(r => ({
            id: r.id,
            countryCode: r.countryCode,
            provinceName: r.provinceName ?? '',
            first: str(r.first),
            firstPrice: str(r.firstPrice),
            continue: str(r.continue),
            continuePrice: str(r.continuePrice),
          }))
        : [{ ...NEW_REGION }]
    );
    setFreeRules(
      existing.freeRules.map(f => ({
        id: f.id,
        countryCode: f.countryCode,
        provinceName: f.provinceName ?? '',
        number: str(f.number),
        price: str(f.price),
      }))
    );
  }, [isEdit, existing]);

  const setRegion = (i: number, patch: Partial<RegionRowEdit>) =>
    setRegions(prev => prev.map((row, idx) => (idx === i ? { ...row, ...patch } : row)));
  const setFreeRule = (i: number, patch: Partial<FreeRuleEdit>) =>
    setFreeRules(prev => prev.map((row, idx) => (idx === i ? { ...row, ...patch } : row)));

  const validNumber = (v: string, allowZero = true): boolean => {
    if (v.trim() === '') return false;
    const n = Number(v);
    return Number.isFinite(n) && (allowZero ? n >= 0 : n > 0);
  };

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!name.trim()) {
      setError('Template name is required.');
      return;
    }
    if (regions.length === 0) {
      setError('At least one region row is required.');
      return;
    }
    for (const [i, row] of regions.entries()) {
      if (!row.countryCode.trim()) {
        setError(`Region row ${i + 1}: country code is required ('*' = any).`);
        return;
      }
      if (!validNumber(row.first, false) || !validNumber(row.continue, false)) {
        setError(`Region row ${i + 1}: 'first' and 'continue' unit counts must be positive numbers.`);
        return;
      }
      if (!validNumber(row.firstPrice) || !validNumber(row.continuePrice)) {
        setError(`Region row ${i + 1}: first price and continue price must be non-negative numbers.`);
        return;
      }
    }
    for (const [i, rule] of freeRules.entries()) {
      if (!rule.countryCode.trim()) {
        setError(`Free rule ${i + 1}: country code is required ('*' = any).`);
        return;
      }
      if (!validNumber(rule.number) || !validNumber(rule.price)) {
        setError(`Free rule ${i + 1}: number and price must be non-negative numbers.`);
        return;
      }
    }

    const detail: IFreightTemplateDetail = {
      template: {
        id: isEdit ? Number(id) : undefined,
        name: name.trim(),
        appoint,
        sortOrder: Number(sortOrder || 100),
        isDefault: isEdit ? existing?.template.isDefault : undefined,
      },
      regions: regions.map(
        (row): IFreightRegionRow => ({
          id: row.id,
          countryCode: row.countryCode.trim() || '*',
          provinceName: row.provinceName.trim() || undefined,
          first: Number(row.first),
          firstPrice: Number(row.firstPrice),
          continue: Number(row.continue),
          continuePrice: Number(row.continuePrice),
        })
      ),
      freeRules: freeRules.map(
        (rule): IFreightFreeRule => ({
          id: rule.id,
          countryCode: rule.countryCode.trim() || '*',
          provinceName: rule.provinceName.trim() || undefined,
          number: Number(rule.number),
          price: Number(rule.price),
        })
      ),
    };

    const res = await (isEdit ? updateTemplate(detail) : createTemplate(detail));
    if ('error' in res) {
      const errData = (res.error as { data?: { errmsg?: string } })?.data;
      setError(errData?.errmsg || 'Request failed.');
      return;
    }
    const msg = errnoMessage(res.data);
    if (msg) {
      setError(msg);
      return;
    }
    navigate('/admin/mall/freight');
  };

  if (isEdit && loading) {
    return (
      <div className='app-container text-center p-5'>
        <span className='spinner-border text-primary' role='status' />
      </div>
    );
  }

  const busy = creating || updating;

  return (
    <div className='app-container'>
      <h5 className='mb-3'>{isEdit ? `Edit freight template #${id}` : 'New freight template'}</h5>
      {error && <div className='alert alert-danger'>{error}</div>}
      <form onSubmit={onSubmit} style={{ maxWidth: 960 }}>
        <div className='row'>
          <div className='col-md-6 mb-3'>
            <label className='form-label'>Name *</label>
            <input className='form-control' value={name} onChange={e => setName(e.target.value)} />
          </div>
          <div className='col-md-4 mb-3'>
            <label className='form-label'>Mode</label>
            <select className='form-select' value={appoint} onChange={e => setAppoint(Number(e.target.value))}>
              {Object.entries(APPOINT_LABELS).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          </div>
          <div className='col-md-2 mb-3'>
            <label className='form-label'>Sort order</label>
            <input className='form-control' type='number' value={sortOrder} onChange={e => setSortOrder(e.target.value)} />
          </div>
        </div>

        <div className='box-card mb-3'>
          <div className='box-card-header d-flex justify-content-between align-items-center'>
            <span>Region rows (first N units cost the first price; each further batch adds the continue price)</span>
            <button type='button' className='btn btn-sm btn-outline-success' onClick={() => setRegions(prev => [...prev, { ...NEW_REGION }])}>
              + Add region
            </button>
          </div>
          <div className='box-card-body'>
            <table className='el-table'>
              <thead>
                <tr>
                  <th style={{ width: 110 }}>Country *</th>
                  <th>Province</th>
                  <th style={{ width: 90 }}>First (units)</th>
                  <th style={{ width: 110 }}>First price</th>
                  <th style={{ width: 100 }}>Continue (units)</th>
                  <th style={{ width: 110 }}>Continue price</th>
                  <th style={{ width: 50 }} />
                </tr>
              </thead>
              <tbody>
                {regions.map((row, i) => (
                  <tr key={row.id ?? `new-${i}`}>
                    <td>
                      <input
                        className='form-control form-control-sm'
                        placeholder='*'
                        value={row.countryCode}
                        onChange={e => setRegion(i, { countryCode: e.target.value })}
                      />
                    </td>
                    <td>
                      <input
                        className='form-control form-control-sm'
                        placeholder='(any)'
                        value={row.provinceName}
                        onChange={e => setRegion(i, { provinceName: e.target.value })}
                      />
                    </td>
                    <td>
                      <input
                        className='form-control form-control-sm'
                        type='number'
                        min='1'
                        value={row.first}
                        onChange={e => setRegion(i, { first: e.target.value })}
                      />
                    </td>
                    <td>
                      <input
                        className='form-control form-control-sm'
                        type='number'
                        step='0.01'
                        min='0'
                        value={row.firstPrice}
                        onChange={e => setRegion(i, { firstPrice: e.target.value })}
                      />
                    </td>
                    <td>
                      <input
                        className='form-control form-control-sm'
                        type='number'
                        min='1'
                        value={row.continue}
                        onChange={e => setRegion(i, { continue: e.target.value })}
                      />
                    </td>
                    <td>
                      <input
                        className='form-control form-control-sm'
                        type='number'
                        step='0.01'
                        min='0'
                        value={row.continuePrice}
                        onChange={e => setRegion(i, { continuePrice: e.target.value })}
                      />
                    </td>
                    <td className='text-end'>
                      <button
                        type='button'
                        className='btn btn-sm btn-outline-danger'
                        disabled={regions.length <= 1}
                        onClick={() => setRegions(prev => prev.filter((_, idx) => idx !== i))}
                      >
                        ×
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div className='small text-muted mt-1'>Country code &apos;*&apos; matches any country; leave province blank to match the whole country.</div>
          </div>
        </div>

        <div className='box-card mb-3'>
          <div className='box-card-header d-flex justify-content-between align-items-center'>
            <span>Free-shipping rules (free when quantity ≥ number OR order amount ≥ price)</span>
            <button type='button' className='btn btn-sm btn-outline-success' onClick={() => setFreeRules(prev => [...prev, { ...NEW_FREE_RULE }])}>
              + Add rule
            </button>
          </div>
          <div className='box-card-body'>
            {freeRules.length === 0 ? (
              <div className='text-muted small'>No free-shipping rules.</div>
            ) : (
              <table className='el-table'>
                <thead>
                  <tr>
                    <th style={{ width: 110 }}>Country *</th>
                    <th>Province</th>
                    <th style={{ width: 130 }}>Number (qty ≥)</th>
                    <th style={{ width: 130 }}>Price (amount ≥)</th>
                    <th style={{ width: 50 }} />
                  </tr>
                </thead>
                <tbody>
                  {freeRules.map((rule, i) => (
                    <tr key={rule.id ?? `new-${i}`}>
                      <td>
                        <input
                          className='form-control form-control-sm'
                          placeholder='*'
                          value={rule.countryCode}
                          onChange={e => setFreeRule(i, { countryCode: e.target.value })}
                        />
                      </td>
                      <td>
                        <input
                          className='form-control form-control-sm'
                          placeholder='(any)'
                          value={rule.provinceName}
                          onChange={e => setFreeRule(i, { provinceName: e.target.value })}
                        />
                      </td>
                      <td>
                        <input
                          className='form-control form-control-sm'
                          type='number'
                          min='0'
                          value={rule.number}
                          onChange={e => setFreeRule(i, { number: e.target.value })}
                        />
                      </td>
                      <td>
                        <input
                          className='form-control form-control-sm'
                          type='number'
                          step='0.01'
                          min='0'
                          value={rule.price}
                          onChange={e => setFreeRule(i, { price: e.target.value })}
                        />
                      </td>
                      <td className='text-end'>
                        <button
                          type='button'
                          className='btn btn-sm btn-outline-danger'
                          onClick={() => setFreeRules(prev => prev.filter((_, idx) => idx !== i))}
                        >
                          ×
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </div>

        <div className='mt-3 mb-3'>
          <button className='btn btn-primary me-2' type='submit' disabled={busy}>
            {busy ? 'Saving…' : 'Save'}
          </button>
          <button className='btn btn-outline-secondary' type='button' onClick={() => navigate('/admin/mall/freight')}>
            Cancel
          </button>
        </div>
      </form>

      {/* Kept outside the <form> so Enter in a preview input never submits the template. */}
      <div style={{ maxWidth: 960 }}>
        {isEdit ? (
          <FreightPreviewWidget tempId={Number(id)} />
        ) : (
          <div className='text-muted small'>Save the template first to preview computed freight quotes.</div>
        )}
      </div>
    </div>
  );
};

export default FreightTemplateForm;
