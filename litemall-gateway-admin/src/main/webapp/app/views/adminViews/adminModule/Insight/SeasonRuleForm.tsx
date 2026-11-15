import { ISeasonRule, useUpdateSeasonRuleMutation } from 'app/shared/reducers/private/services/insightApi';
import { errnoMessage } from 'app/views/adminViews/adminModule/_shared/crudUi';
import {
  addTerms,
  diffDraft,
  isEmptyPatch,
  SeasonRuleDraft,
  toDraft,
  validateDraft,
  WEIGHT_FIELDS,
  WEIGHT_MAX,
  WEIGHT_MIN,
} from 'app/views/adminViews/adminModule/Insight/seasonFormat';
import * as React from 'react';

// Edit one season rule. The server stores terms / categories / weights as raw
// JSON text and validates nothing, so this form never offers a JSON box: it
// edits structured values and seasonFormat.ts serialises them. Only changed
// fields are sent (an absent field means "leave it alone" to the endpoint).

interface ChipProps {
  label: string;
  hint: string;
  values: string[];
  error?: string;
  onChange: (next: string[]) => void;
}

const ChipInput: React.FC<ChipProps> = ({ label, hint, values, error, onChange }) => {
  const [typed, setTyped] = React.useState('');
  const commit = () => {
    if (!typed.trim()) return;
    onChange(addTerms(values, typed));
    setTyped('');
  };
  return (
    <div className='mb-3'>
      <label className='form-label'>{label}</label>
      <div className='mb-1'>
        {values.length === 0 && <span className='text-muted small'>None.</span>}
        {values.map(v => (
          <span key={v} className='badge bg-light text-dark border me-1 mb-1'>
            {v}
            <button
              type='button'
              className='btn-close ms-1'
              style={{ fontSize: '0.6em' }}
              aria-label={`Remove ${label.toLowerCase()} ${v}`}
              onClick={() => onChange(values.filter(x => x !== v))}
            />
          </span>
        ))}
      </div>
      <div className='input-group' style={{ maxWidth: 420 }}>
        <input
          className='form-control'
          value={typed}
          aria-label={`Add to ${label.toLowerCase()}`}
          placeholder='Type a term, Enter to add'
          onChange={e => setTyped(e.target.value)}
          onKeyDown={e => {
            if (e.key === 'Enter') {
              e.preventDefault();
              commit();
            }
          }}
        />
        <button type='button' className='btn btn-outline-secondary' onClick={commit}>
          Add
        </button>
      </div>
      <div className='form-text'>{hint}</div>
      {error && <div className='text-danger small'>{error}</div>}
    </div>
  );
};

interface Props {
  rule: ISeasonRule;
  onClose: () => void;
}

const SeasonRuleForm: React.FC<Props> = ({ rule, onClose }) => {
  const [draft, setDraft] = React.useState<SeasonRuleDraft>(() => toDraft(rule));
  const [categoryText, setCategoryText] = React.useState(() => toDraft(rule).categoryIds.join(', '));
  const [errors, setErrors] = React.useState<Record<string, string>>({});
  const [message, setMessage] = React.useState<{ kind: 'success' | 'danger' | 'warning'; text: string } | null>(null);
  const [update, { isLoading: saving }] = useUpdateSeasonRuleMutation();

  const set = <K extends keyof SeasonRuleDraft>(key: K, value: SeasonRuleDraft[K]) => setDraft(d => ({ ...d, [key]: value }));

  const onSave = async () => {
    setMessage(null);
    // Category ids are typed as "1036143, 1036495"; anything else is refused here.
    const pieces = categoryText
      .split(',')
      .map(p => p.trim())
      .filter(Boolean);
    const ids = pieces.map(Number);
    const found = validateDraft(draft);
    if (ids.some(n => !Number.isInteger(n) || n <= 0)) found.categoryIds = 'Category ids must be whole numbers, separated by commas.';
    setErrors(found);
    if (Object.keys(found).length > 0) return;

    const candidate = { ...draft, categoryIds: ids.filter((n, i) => ids.indexOf(n) === i) };
    const { patch, cannotClear } = diffDraft(rule, candidate);
    if (cannotClear.length > 0) {
      setMessage({
        kind: 'warning',
        text: `The ${cannotClear.join(' and ')} cannot be emptied from here — enter a value instead (0 for no lower bound).`,
      });
      return;
    }
    if (isEmptyPatch(patch)) {
      setMessage({ kind: 'warning', text: 'Nothing changed.' });
      return;
    }
    const res = await update({ key: rule.seasonKey, patch: patch as Record<string, unknown> });
    if ('error' in res && res.error) {
      const s = (res.error as { status?: number | string })?.status;
      setMessage({ kind: 'danger', text: `Save failed${s ? ` (${s})` : ''}. Nothing was changed.` });
      return;
    }
    const msg = errnoMessage((res as { data?: unknown }).data);
    if (msg) {
      setMessage({ kind: 'danger', text: msg });
      return;
    }
    setMessage({ kind: 'success', text: 'Saved. The change takes effect at the next scoring run (04:35, or Run scorer now).' });
  };

  return (
    <div className='card mt-3'>
      <div className='card-body'>
        <div className='d-flex justify-content-between align-items-center mb-3'>
          <h5 className='mb-0'>
            Edit season: {rule.name || rule.seasonKey} <span className='text-muted small'>({rule.seasonKey})</span>
          </h5>
          <button type='button' className='btn btn-sm btn-outline-secondary' onClick={onClose}>
            Close
          </button>
        </div>

        {message && <div className={`alert alert-${message.kind}`}>{message.text}</div>}

        <div className='row'>
          <div className='col-md-4 mb-3'>
            <label className='form-label' htmlFor='season-name'>
              Name
            </label>
            <input id='season-name' className='form-control' value={draft.name} onChange={e => set('name', e.target.value)} />
            {errors.name && <div className='text-danger small'>{errors.name}</div>}
          </div>
          <div className='col-md-2 mb-3'>
            <label className='form-label' htmlFor='season-start'>
              Window start
            </label>
            <input
              id='season-start'
              className='form-control'
              placeholder='MM-DD'
              value={draft.windowStartMd}
              onChange={e => set('windowStartMd', e.target.value.trim())}
            />
            {errors.windowStartMd && <div className='text-danger small'>{errors.windowStartMd}</div>}
          </div>
          <div className='col-md-2 mb-3'>
            <label className='form-label' htmlFor='season-end'>
              Window end
            </label>
            <input
              id='season-end'
              className='form-control'
              placeholder='MM-DD'
              value={draft.windowEndMd}
              onChange={e => set('windowEndMd', e.target.value.trim())}
            />
            {errors.windowEndMd && <div className='text-danger small'>{errors.windowEndMd}</div>}
          </div>
          <div className='col-md-4 mb-3 d-flex align-items-end'>
            <div className='form-check'>
              <input
                id='season-enabled'
                type='checkbox'
                className='form-check-input'
                checked={draft.enabled}
                onChange={e => set('enabled', e.target.checked)}
              />
              <label className='form-check-label' htmlFor='season-enabled'>
                Enabled (off = nothing is scored or published for this season)
              </label>
            </div>
          </div>
        </div>
        <div className='form-text mb-3'>Both window dates are inclusive; a window may wrap the year end (12-01 to 02-28).</div>

        <ChipInput
          label='Match terms'
          hint='A product is a candidate when one of these words is in its title (whole word, plural-tolerant).'
          values={draft.matchTerms}
          error={errors.matchTerms}
          onChange={v => set('matchTerms', v)}
        />
        <ChipInput
          label='Exclude terms'
          hint='A title containing one of these is never a candidate for this season, whatever else it matches.'
          values={draft.excludeTerms}
          error={errors.excludeTerms}
          onChange={v => set('excludeTerms', v)}
        />

        <div className='row'>
          <div className='col-md-4 mb-3'>
            <label className='form-label' htmlFor='season-categories'>
              Boosted category ids
            </label>
            <input
              id='season-categories'
              className='form-control'
              placeholder='e.g. 1036143, 1036495'
              value={categoryText}
              onChange={e => setCategoryText(e.target.value)}
            />
            {errors.categoryIds && <div className='text-danger small'>{errors.categoryIds}</div>}
          </div>
          <div className='col-md-2 mb-3'>
            <label className='form-label' htmlFor='season-price-min'>
              Price from (€)
            </label>
            <input id='season-price-min' className='form-control' value={draft.priceMin} onChange={e => set('priceMin', e.target.value)} />
            {errors.priceMin && <div className='text-danger small'>{errors.priceMin}</div>}
          </div>
          <div className='col-md-2 mb-3'>
            <label className='form-label' htmlFor='season-price-max'>
              Price to (€)
            </label>
            <input id='season-price-max' className='form-control' value={draft.priceMax} onChange={e => set('priceMax', e.target.value)} />
            {errors.priceMax && <div className='text-danger small'>{errors.priceMax}</div>}
          </div>
        </div>

        <h6>Weights</h6>
        <div className='form-text mb-2'>
          Each is a multiplier between {WEIGHT_MIN.toFixed(1)} and {WEIGHT_MAX.toFixed(1)}; 1.0 means no effect. Profitability, stock and the
          price floor are checked for every season and cannot be tuned here.
        </div>
        <div className='row'>
          {WEIGHT_FIELDS.map(f => (
            <div className='col-md-3 mb-3' key={f.key}>
              <label className='form-label' htmlFor={`season-weight-${f.key}`}>
                {f.label}
              </label>
              <input
                id={`season-weight-${f.key}`}
                type='number'
                className='form-control'
                min={WEIGHT_MIN}
                max={WEIGHT_MAX}
                step={0.05}
                value={Number.isFinite(draft.weights[f.key]) ? draft.weights[f.key] : ''}
                onChange={e => set('weights', { ...draft.weights, [f.key]: e.target.value === '' ? NaN : Number(e.target.value) })}
              />
              <div className='form-text'>{f.hint}</div>
              {errors[f.key] && <div className='text-danger small'>{errors[f.key]}</div>}
            </div>
          ))}
        </div>

        <button type='button' className='btn btn-primary' disabled={saving} onClick={onSave}>
          {saving ? 'Saving…' : 'Save changes'}
        </button>
      </div>
    </div>
  );
};

export default SeasonRuleForm;
