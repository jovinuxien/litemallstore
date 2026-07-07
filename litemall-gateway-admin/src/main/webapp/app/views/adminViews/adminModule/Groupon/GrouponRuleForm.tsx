import { IGrouponRule } from 'app/shared/model/admin/promotion-system.model';
import { useCreateGrouponRuleMutation, useListGrouponRulesQuery, useUpdateGrouponRuleMutation } from 'app/shared/reducers/private/services/adminPromotionApi';
import { errnoMessage } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { useNavigate, useParams } from 'react-router-dom';

// Create / edit a groupon rule. Server requires goodsId, discount,
// discountMember and expireTime; goodsName/picUrl are resolved server-side from
// the goods. There is no per-rule read endpoint, so the edit form hydrates from
// the rule list (small dataset).

const empty: IGrouponRule = { goodsId: undefined, discount: 0, discountMember: 2, expireTime: '' };

const GrouponRuleForm: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const isEdit = Boolean(id);
  const navigate = useNavigate();

  // No /groupon/read; pull the rule out of the (typically small) rule list.
  const { data: rules, isLoading: loading } = useListGrouponRulesQuery({ page: 1, limit: 200, sort: 'add_time', order: 'desc' }, { skip: !isEdit });
  const [createRule, { isLoading: creating }] = useCreateGrouponRuleMutation();
  const [updateRule, { isLoading: updating }] = useUpdateGrouponRuleMutation();

  const [form, setForm] = React.useState<IGrouponRule>(empty);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (isEdit && rules?.list) {
      const found = rules.list.find(r => String(r.id) === id);
      if (found) setForm(found);
    }
  }, [isEdit, rules, id]);

  const set = (patch: Partial<IGrouponRule>) => setForm(prev => ({ ...prev, ...patch }));

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!form.goodsId) {
      setError('Goods ID is required.');
      return;
    }
    if (!form.expireTime) {
      setError('Expiry time is required.');
      return;
    }
    const body: IGrouponRule = {
      ...form,
      goodsId: Number(form.goodsId),
      discount: Number(form.discount ?? 0),
      discountMember: Number(form.discountMember ?? 2),
    };
    const res = await (isEdit ? updateRule(body) : createRule(body));
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) {
      setError(msg);
      return;
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
      <h5 className='mb-3'>{isEdit ? `Edit groupon rule #${id}` : 'New groupon rule'}</h5>
      {error && <div className='alert alert-danger'>{error}</div>}
      <form onSubmit={onSubmit} style={{ maxWidth: 640 }}>
        <div className='mb-3'>
          <label className='form-label'>Goods ID *</label>
          <input className='form-control' type='number' value={form.goodsId ?? ''} onChange={e => set({ goodsId: e.target.value ? Number(e.target.value) : undefined })} disabled={isEdit} />
          {isEdit && form.goodsName && <div className='form-text'>{form.goodsName}</div>}
        </div>
        <div className='row'>
          <div className='col-md-6 mb-3'>
            <label className='form-label'>Discount (¥ off)</label>
            <input className='form-control' type='number' step='0.01' value={form.discount ?? 0} onChange={e => set({ discount: Number(e.target.value) })} />
          </div>
          <div className='col-md-6 mb-3'>
            <label className='form-label'>Member size (people to form a group)</label>
            <input className='form-control' type='number' value={form.discountMember ?? 2} onChange={e => set({ discountMember: Number(e.target.value) })} />
          </div>
        </div>
        <div className='mb-3'>
          <label className='form-label'>Expiry time *</label>
          <input className='form-control' type='datetime-local' value={dtLocal(form.expireTime)} onChange={e => set({ expireTime: e.target.value })} />
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
