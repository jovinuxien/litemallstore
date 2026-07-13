import { ITopic, useCreateTopicMutation, useReadTopicQuery, useUpdateTopicMutation } from 'app/shared/reducers/private/services/adminParityApi';
import { errnoMessage } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { useNavigate, useParams } from 'react-router-dom';

// Create / edit a topic (专题). With an :id route param it loads the existing
// row and posts an update; otherwise it creates. The related-goods field is a
// comma-separated id list in the UI, mapped to/from the server's array shape
// defensively (the row may arrive as number[] or as a raw string).
// Contract: goods-management docs/handoff-content-endpoints.md §5.

const LIST_ROUTE = '/admin/promotion/topic';

// Server → input: number[] or string → "1, 2, 3".
const goodsToText = (goods: ITopic['goods']): string => {
  if (Array.isArray(goods)) return goods.join(', ');
  if (typeof goods === 'string') {
    // Tolerate a JSON-array string like "[1,2,3]".
    const trimmed = goods.trim();
    if (trimmed.startsWith('[')) {
      try {
        const parsed = JSON.parse(trimmed);
        if (Array.isArray(parsed)) return parsed.join(', ');
      } catch {
        /* fall through to the raw string */
      }
    }
    return trimmed;
  }
  return '';
};

// Input → server: "1, 2,3" → [1, 2, 3]; returns null when a token isn't numeric.
const textToGoods = (text: string): number[] | null => {
  const tokens = text
    .split(/[,\s]+/)
    .map(t => t.trim())
    .filter(Boolean);
  const ids: number[] = [];
  for (const t of tokens) {
    if (!/^\d+$/.test(t)) return null;
    ids.push(Number(t));
  }
  return ids;
};

const TopicForm: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const isEdit = Boolean(id);
  const navigate = useNavigate();

  const { data: existing, isLoading: loading } = useReadTopicQuery(id as string, { skip: !isEdit });
  const [createTopic, { isLoading: creating }] = useCreateTopicMutation();
  const [updateTopic, { isLoading: updating }] = useUpdateTopicMutation();

  const [form, setForm] = React.useState<ITopic>({ title: '', subtitle: '', price: 0, picUrl: '', sortOrder: 0, content: '' });
  const [goodsText, setGoodsText] = React.useState('');
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (isEdit && existing && existing.id != null) {
      setForm(existing);
      setGoodsText(goodsToText(existing.goods));
    }
  }, [isEdit, existing]);

  const set = (patch: Partial<ITopic>) => setForm(prev => ({ ...prev, ...patch }));

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!form.title?.trim()) {
      setError('Title is required.');
      return;
    }
    if (form.price != null && Number.isNaN(Number(form.price))) {
      setError('Price must be a number.');
      return;
    }
    const goods = textToGoods(goodsText);
    if (goods === null) {
      setError('Goods must be a comma-separated list of numeric ids.');
      return;
    }
    const body: ITopic = {
      ...form,
      title: form.title.trim(),
      price: form.price == null ? undefined : Number(form.price),
      sortOrder: Number(form.sortOrder ?? 0),
      goods,
    };
    const res = await (isEdit ? updateTopic(body) : createTopic(body));
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) {
      setError(msg);
      return;
    }
    navigate(LIST_ROUTE);
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
      <h5 className='mb-3'>{isEdit ? `Edit topic #${id}` : 'New topic'}</h5>
      {error && <div className='alert alert-danger'>{error}</div>}
      <form onSubmit={onSubmit} style={{ maxWidth: 640 }}>
        <div className='mb-3'>
          <label className='form-label'>Title *</label>
          <input className='form-control' value={form.title ?? ''} onChange={e => set({ title: e.target.value })} />
        </div>
        <div className='mb-3'>
          <label className='form-label'>Subtitle</label>
          <input className='form-control' value={form.subtitle ?? ''} onChange={e => set({ subtitle: e.target.value })} />
        </div>
        <div className='mb-3'>
          <label className='form-label'>Image URL</label>
          <input className='form-control' value={form.picUrl ?? ''} onChange={e => set({ picUrl: e.target.value })} />
          {form.picUrl && <img src={form.picUrl} alt='' className='cell-thumb mt-2' />}
        </div>
        <div className='row'>
          <div className='col mb-3'>
            <label className='form-label'>Price</label>
            <input
              className='form-control'
              type='number'
              step='0.01'
              value={form.price ?? 0}
              onChange={e => set({ price: e.target.value === '' ? undefined : Number(e.target.value) })}
            />
          </div>
          <div className='col mb-3'>
            <label className='form-label'>Sort order</label>
            <input className='form-control' type='number' value={form.sortOrder ?? 0} onChange={e => set({ sortOrder: Number(e.target.value) })} />
          </div>
        </div>
        <div className='mb-3'>
          <label className='form-label'>Goods ids</label>
          <input className='form-control' placeholder='e.g. 1181000, 1181001' value={goodsText} onChange={e => setGoodsText(e.target.value)} />
          <div className='form-text'>Comma-separated goods ids featured by this topic.</div>
        </div>
        <div className='mb-3'>
          <label className='form-label'>Content</label>
          <textarea className='form-control' rows={6} value={form.content ?? ''} onChange={e => set({ content: e.target.value })} />
        </div>
        <div className='mt-3'>
          <button className='btn btn-primary me-2' type='submit' disabled={busy}>
            {busy ? 'Saving…' : 'Save'}
          </button>
          <button className='btn btn-outline-secondary' type='button' onClick={() => navigate(LIST_ROUTE)}>
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
};

export default TopicForm;
