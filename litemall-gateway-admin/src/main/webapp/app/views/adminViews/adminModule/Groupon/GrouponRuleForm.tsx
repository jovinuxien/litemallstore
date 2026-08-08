import { ICombination } from 'app/shared/model/admin/promotion-system.model';
import {
  promotionOpMessage,
  useCreateCombinationMutation,
  useReadCombinationQuery,
  useUpdateCombinationMutation,
} from 'app/shared/reducers/private/services/adminPromotionApi';
import { useConsumePromoCandidateMutation } from 'app/shared/reducers/private/services/insightApi';
import { GrouponPromoPrefill, createdRefId } from 'app/views/adminViews/adminModule/Insight/promoFormat';
import * as React from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';

// Create / edit a group-buy (combination) campaign against promotion-service.
// Create needs goodsId + title + prices + requiredMembers + window; the
// update command carries no goodsId (the campaign stays bound to its goods).
// New campaigns start in DRAFT — activate from the list view. That holds for
// Wave-19 promo-suggestion prefills too: suggested campaigns are NEVER
// auto-activated (Phase-3 gating decision).

const empty: ICombination = {
  goodsId: undefined,
  title: '',
  picUrl: '',
  combinationPrice: 0,
  originalPrice: 0,
  requiredMembers: 2,
  limitPerUser: 1,
  startTime: '',
  endTime: '',
};

const GrouponRuleForm: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const isEdit = Boolean(id);
  const navigate = useNavigate();
  const location = useLocation();

  // Wave 19: the promo-suggestions panel opens this form prefilled via router
  // state; absent state changes nothing. Captured once — the suggestion only
  // seeds the initial form, the admin's edits win from then on.
  const [prefill] = React.useState<GrouponPromoPrefill | null>(
    () => (!isEdit && (location.state as { promoPrefill?: GrouponPromoPrefill } | null)?.promoPrefill) || null
  );

  const { data: existing, isLoading: loading } = useReadCombinationQuery(id as string, { skip: !isEdit });
  const [createCombination, { isLoading: creating }] = useCreateCombinationMutation();
  const [updateCombination, { isLoading: updating }] = useUpdateCombinationMutation();
  const [consumeCandidate] = useConsumePromoCandidateMutation();

  const [form, setForm] = React.useState<ICombination>(() => (prefill ? { ...empty, ...prefill.combination } : empty));
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (isEdit && existing && existing.id != null) setForm(existing);
  }, [isEdit, existing]);

  const set = (patch: Partial<ICombination>) => setForm(prev => ({ ...prev, ...patch }));

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!isEdit && !form.goodsId) {
      setError('Goods ID is required.');
      return;
    }
    if (!form.title?.trim()) {
      setError('Title is required.');
      return;
    }
    if (!form.startTime || !form.endTime) {
      setError('Campaign start and end time are required.');
      return;
    }
    const body: ICombination = {
      ...form,
      goodsId: form.goodsId != null ? Number(form.goodsId) : undefined,
      combinationPrice: Number(form.combinationPrice ?? 0),
      originalPrice: Number(form.originalPrice ?? 0),
      requiredMembers: Number(form.requiredMembers ?? 2),
      limitPerUser: Number(form.limitPerUser ?? 1),
    };
    const res = await (isEdit ? updateCombination(body) : createCombination(body));
    const msg = promotionOpMessage(res);
    if (msg) {
      setError(msg);
      return;
    }
    // Wave 19: opened from a promo suggestion → record the consumption with
    // the created combination id. FAIL-SOFT by construction: the un-unwrapped
    // mutation promise never rejects, and the result is deliberately ignored —
    // a failed consume must never block or roll back the create. The campaign
    // itself stays DRAFT (no auto-activation — Phase-3 gating decision).
    if (!isEdit && prefill) {
      consumeCandidate({ ...prefill.consume, refId: createdRefId(res, 'combinationId') });
    }
    navigate('/admin/promotion/groupon-rule');
  };

  if (isEdit && loading) {
    return (
      <div className='app-container text-center p-5'>
        <span className='spinner-border text-primary' role='status' />
      </div>
    );
  }

  const busy = creating || updating;
  // datetime-local wants 'YYYY-MM-DDTHH:mm'; the server returns full ISO.
  const dtLocal = (v?: string) => (v ? v.slice(0, 16) : '');

  return (
    <div className='app-container'>
      <h5 className='mb-3'>{isEdit ? `Edit group-buy campaign #${id}` : 'New group-buy campaign'}</h5>
      {error && <div className='alert alert-danger'>{error}</div>}
      <form onSubmit={onSubmit} style={{ maxWidth: 640 }}>
        <div className='row'>
          <div className='col-md-4 mb-3'>
            <label className='form-label'>Goods ID *</label>
            <input
              className='form-control'
              type='number'
              value={form.goodsId ?? ''}
              onChange={e => set({ goodsId: e.target.value ? Number(e.target.value) : undefined })}
              disabled={isEdit}
            />
          </div>
          <div className='col-md-8 mb-3'>
            <label className='form-label'>Title *</label>
            <input className='form-control' value={form.title ?? ''} onChange={e => set({ title: e.target.value })} />
          </div>
        </div>
        <div className='mb-3'>
          <label className='form-label'>Image URL</label>
          <input className='form-control' value={form.picUrl ?? ''} onChange={e => set({ picUrl: e.target.value })} />
        </div>
        <div className='row'>
          <div className='col-md-3 mb-3'>
            <label className='form-label'>Group price</label>
            <input className='form-control' type='number' step='0.01' value={form.combinationPrice ?? 0} onChange={e => set({ combinationPrice: Number(e.target.value) })} />
          </div>
          <div className='col-md-3 mb-3'>
            <label className='form-label'>Original price</label>
            <input className='form-control' type='number' step='0.01' value={form.originalPrice ?? 0} onChange={e => set({ originalPrice: Number(e.target.value) })} />
          </div>
          <div className='col-md-3 mb-3'>
            <label className='form-label'>Members to form</label>
            <input className='form-control' type='number' value={form.requiredMembers ?? 2} onChange={e => set({ requiredMembers: Number(e.target.value) })} />
          </div>
          <div className='col-md-3 mb-3'>
            <label className='form-label'>Per-user limit</label>
            <input className='form-control' type='number' value={form.limitPerUser ?? 1} onChange={e => set({ limitPerUser: Number(e.target.value) })} />
          </div>
        </div>
        <div className='row'>
          <div className='col-md-6 mb-3'>
            <label className='form-label'>Starts *</label>
            <input className='form-control' type='datetime-local' value={dtLocal(form.startTime)} onChange={e => set({ startTime: e.target.value })} />
          </div>
          <div className='col-md-6 mb-3'>
            <label className='form-label'>Ends *</label>
            <input className='form-control' type='datetime-local' value={dtLocal(form.endTime)} onChange={e => set({ endTime: e.target.value })} />
          </div>
        </div>
        <div className='mt-3'>
          <button className='btn btn-primary me-2' type='submit' disabled={busy}>
            {busy ? 'Saving…' : 'Save'}
          </button>
          <button className='btn btn-outline-secondary' type='button' onClick={() => navigate('/admin/promotion/groupon-rule')}>
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
};

export default GrouponRuleForm;
