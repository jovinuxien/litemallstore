import {
  IPageComponent,
  IPalette,
  IPaletteComponent,
  IPaletteField,
  PagePosition,
  useCreatePageMutation,
  useGetPagePaletteQuery,
  useReadPageQuery,
  useUpdatePageMutation,
} from 'app/shared/reducers/private/services/adminContentApi';
import { useUploadStorageMutation } from 'app/shared/reducers/private/services/adminSysApi';
import { errnoMessage, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { useNavigate, useParams } from 'react-router-dom';

// DIY page editor — a STRUCTURED palette editor (deliberately NOT drag-and-
// drop). The component catalog and every per-component form are GENERATED
// from GET /srv/private/admin/page/palette (the machine-readable schema the
// server validates against), so the editor can never drift from what the
// backend enforces. Left: the ordered component list (move up/down, remove,
// collapse) plus an add-component select; each expanded card shows its
// schema-driven form. Conditional requirements (goods-list: byIds requires
// goodsIds, byCategory requires categoryId) are soft-validated client-side,
// but the server's errno 640 errmsg — which NAMES the offending component —
// is always surfaced verbatim on save. Optional preview pane approximates the
// customer renderer, resolving dynamic components through the customer
// endpoints with degrade rule R1 (any failure or empty list → the component
// is skipped silently, never an error).
// Contract: litemall-goods-management/docs/spec-page-palette-v1.md.

interface EditorComponent extends IPageComponent {
  key: string; // always present in the editor (stable React list key)
}

const makeKey = (type: string): string => `${type}-${Math.random().toString(36).slice(2, 10)}`;

const condLabel = (field: IPaletteField): string | null => (field.requiredWhen ? `required when ${field.requiredWhen}` : null);

/** Parse a palette `requiredWhen` clause like "mode=byIds". */
const parseCond = (cond?: string): { field: string; value: string } | null => {
  if (!cond) return null;
  const eq = cond.indexOf('=');
  if (eq < 0) return null;
  return { field: cond.slice(0, eq), value: cond.slice(eq + 1) };
};

const condMet = (field: IPaletteField, config: Record<string, unknown>): boolean => {
  const cond = parseCond(field.requiredWhen);
  if (!cond) return true;
  return config[cond.field] === cond.value;
};

// ----- soft validation (client-side mirror; the server's 640 errmsg wins) ---

const softIssues = (comp: EditorComponent, schema: IPaletteComponent | undefined, index: number): string[] => {
  if (!schema) return [];
  const issues: string[] = [];
  const label = `component[${index}] (${comp.type})`;
  for (const field of schema.fields) {
    const value = comp.config[field.name];
    const empty =
      value === undefined ||
      value === null ||
      (typeof value === 'string' && value.trim() === '') ||
      (Array.isArray(value) && value.length === 0);
    const required = field.required || (Boolean(field.requiredWhen) && condMet(field, comp.config));
    if (required && empty) {
      issues.push(field.requiredWhen ? `${label}: ${field.requiredWhen} requires ${field.name}` : `${label}: ${field.name} is required`);
      continue;
    }
    if (empty) continue;
    if (field.type === 'int' && typeof value === 'number') {
      if (field.min != null && value < field.min) issues.push(`${label}: ${field.name} must be at least ${field.min}`);
      if (field.max != null && value > field.max) issues.push(`${label}: ${field.name} must be at most ${field.max}`);
    }
    if ((field.type === 'int[]' || field.type === 'array') && Array.isArray(value)) {
      if (field.minItems != null && value.length < field.minItems) issues.push(`${label}: ${field.name} needs at least ${field.minItems} item(s)`);
      if (field.maxItems != null && value.length > field.maxItems) issues.push(`${label}: ${field.name} allows at most ${field.maxItems} item(s)`);
      if (field.type === 'array' && field.itemFields) {
        (value as Record<string, unknown>[]).forEach((row, j) => {
          field.itemFields
            ?.filter(f => f.required)
            .forEach(f => {
              const v = row?.[f.name];
              if (v === undefined || v === null || (typeof v === 'string' && v.trim() === '')) {
                issues.push(`${label}: ${field.name}[${j}].${f.name} is required`);
              }
            });
        });
      }
    }
  }
  return issues;
};

// ----- summaries for the collapsed cards ------------------------------------

const summarize = (comp: EditorComponent): string => {
  const cfg = comp.config;
  switch (comp.type) {
    case 'banner':
      return String(cfg.title || cfg.image || 'no image yet');
    case 'image-row': {
      const n = Array.isArray(cfg.images) ? cfg.images.length : 0;
      return `${n} image${n === 1 ? '' : 's'}`;
    }
    case 'goods-list': {
      const mode = String(cfg.mode ?? 'mode not set');
      if (cfg.mode === 'byIds') return `byIds: ${Array.isArray(cfg.goodsIds) ? cfg.goodsIds.length : 0} id(s)`;
      if (cfg.mode === 'byCategory') return `byCategory: ${cfg.categoryId ?? '?'} (limit ${cfg.limit ?? 8})`;
      return `${mode} (limit ${cfg.limit ?? 8})`;
    }
    case 'coupon-strip':
    case 'seckill-strip':
      return String(cfg.title || `limit ${cfg.limit ?? 3}`);
    case 'article-strip':
      return `${cfg.title || `limit ${cfg.limit ?? 3}`}${cfg.hotOnly ? ' (hot only)' : ''}`;
    case 'rich-text': {
      const text = String(cfg.html ?? '').replace(/<[^>]*>/g, '').trim();
      return text ? `${text.slice(0, 60)}${text.length > 60 ? '…' : ''}` : 'empty';
    }
    default:
      return '';
  }
};

// ----- small field widgets ---------------------------------------------------

/** Image URL input with an upload button (storage vertical, multipart). */
const ImageUrlInput: React.FC<{ value: string; onChange: (v: string) => void; placeholder?: string }> = ({ value, onChange, placeholder }) => {
  const [uploadStorage, { isLoading: uploading }] = useUploadStorageMutation();
  const [uploadError, setUploadError] = React.useState<string | null>(null);
  const fileRef = React.useRef<HTMLInputElement>(null);

  const onUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setUploadError(null);
    const fd = new FormData();
    fd.append('file', file);
    const res = await uploadStorage(fd);
    if (fileRef.current) fileRef.current.value = '';
    if (!('data' in res)) {
      setUploadError('Upload failed.');
      return;
    }
    const msg = errnoMessage(res.data);
    if (msg) {
      setUploadError(msg);
      return;
    }
    const url = res.data?.data?.url;
    if (url) onChange(url);
  };

  return (
    <div>
      <div className='d-flex gap-2'>
        <input className='form-control form-control-sm' placeholder={placeholder ?? 'Image URL'} value={value} onChange={e => onChange(e.target.value)} />
        <label className='btn btn-sm btn-outline-secondary mb-0' style={{ whiteSpace: 'nowrap' }}>
          {uploading ? 'Uploading…' : 'Upload'}
          <input ref={fileRef} type='file' accept='image/*' hidden onChange={onUpload} disabled={uploading} />
        </label>
      </div>
      {uploadError && <div className='text-danger small mt-1'>{uploadError}</div>}
      {value && <img src={value} alt='' className='mt-1' style={{ maxHeight: 60, maxWidth: '100%', objectFit: 'contain' }} />}
    </div>
  );
};

/** Comma-separated int[] input keeping raw text locally so typing stays natural. */
const IntArrayInput: React.FC<{ value: number[]; onChange: (v: number[]) => void }> = ({ value, onChange }) => {
  const [text, setText] = React.useState(value.join(', '));

  const onText = (t: string) => {
    setText(t);
    const parsed = t
      .split(/[\s,]+/)
      .map(s => s.trim())
      .filter(Boolean)
      .map(Number)
      .filter(n => Number.isInteger(n) && n > 0);
    onChange(parsed);
  };

  return (
    <input
      className='form-control form-control-sm'
      placeholder='e.g. 1181000, 1181001'
      value={text}
      onChange={e => onText(e.target.value)}
      onBlur={() => setText(value.join(', '))}
    />
  );
};

// ----- schema-driven field --------------------------------------------------

interface FieldProps {
  compKey: string;
  field: IPaletteField;
  config: Record<string, unknown>;
  onChange: (name: string, value: unknown) => void;
}

const SchemaField: React.FC<FieldProps> = ({ compKey, field, config, onChange }) => {
  const value = config[field.name];
  const hint = [field.description, condLabel(field), field.default !== undefined ? `default ${String(field.default)}` : null]
    .filter(Boolean)
    .join(' · ');

  const label = (
    <label className='form-label mb-1 small fw-semibold'>
      {field.name}
      {field.required && ' *'}
    </label>
  );

  // string — html gets a big textarea with the sanitize hint, image fields get
  // the upload widget, everything else a plain input.
  if (field.type === 'string') {
    if (field.name === 'html') {
      return (
        <div className='mb-2'>
          {label}
          <textarea
            className='form-control form-control-sm font-monospace'
            rows={8}
            value={String(value ?? '')}
            onChange={e => onChange(field.name, e.target.value || undefined)}
          />
          <div className='form-text'>Rich HTML — the server sanitizes it on save (clean-and-store); disallowed markup is stripped, never rejected.</div>
        </div>
      );
    }
    if (field.name === 'image') {
      return (
        <div className='mb-2'>
          {label}
          <ImageUrlInput value={String(value ?? '')} onChange={v => onChange(field.name, v || undefined)} />
          {hint && <div className='form-text'>{hint}</div>}
        </div>
      );
    }
    return (
      <div className='mb-2'>
        {label}
        <input className='form-control form-control-sm' value={String(value ?? '')} onChange={e => onChange(field.name, e.target.value || undefined)} />
        {hint && <div className='form-text'>{hint}</div>}
      </div>
    );
  }

  if (field.type === 'int') {
    return (
      <div className='mb-2'>
        {label}
        <input
          className='form-control form-control-sm'
          style={{ maxWidth: 160 }}
          type='number'
          min={field.min}
          max={field.max}
          placeholder={field.default !== undefined ? String(field.default) : undefined}
          value={value === undefined || value === null ? '' : Number(value)}
          onChange={e => onChange(field.name, e.target.value === '' ? undefined : Number(e.target.value))}
        />
        {hint && <div className='form-text'>{hint}</div>}
      </div>
    );
  }

  if (field.type === 'boolean') {
    const id = `${compKey}-${field.name}`;
    return (
      <div className='form-check mb-2'>
        <input id={id} className='form-check-input' type='checkbox' checked={Boolean(value)} onChange={e => onChange(field.name, e.target.checked)} />
        <label className='form-check-label small' htmlFor={id}>
          {field.name}
          {hint && <span className='text-muted'> — {hint}</span>}
        </label>
      </div>
    );
  }

  if (field.type === 'enum') {
    return (
      <div className='mb-2'>
        {label}
        <select
          className='form-select form-select-sm'
          style={{ maxWidth: 220 }}
          value={String(value ?? '')}
          onChange={e => onChange(field.name, e.target.value || undefined)}
          aria-label={field.name}
        >
          <option value=''>Select…</option>
          {(field.values ?? []).map(v => (
            <option key={v} value={v}>
              {v}
            </option>
          ))}
        </select>
        {hint && <div className='form-text'>{hint}</div>}
      </div>
    );
  }

  if (field.type === 'int[]') {
    return (
      <div className='mb-2'>
        {label}
        <IntArrayInput value={Array.isArray(value) ? (value as number[]) : []} onChange={v => onChange(field.name, v.length ? v : undefined)} />
        <div className='form-text'>
          Comma-separated ids
          {field.minItems != null && field.maxItems != null && ` (${field.minItems}–${field.maxItems})`}
          {hint && ` · ${hint}`}
        </div>
      </div>
    );
  }

  // array with itemFields — the image-row dynamic row editor.
  if (field.type === 'array' && field.itemFields) {
    const rows = Array.isArray(value) ? (value as Record<string, unknown>[]) : [];
    const setRows = (next: Record<string, unknown>[]) => onChange(field.name, next.length ? next : undefined);
    const maxItems = field.maxItems ?? 8;
    return (
      <div className='mb-2'>
        {label}
        {rows.map((row, j) => (
          <div key={j} className='border rounded p-2 mb-2'>
            <div className='d-flex justify-content-between align-items-center mb-1'>
              <span className='text-muted small'>row {j + 1}</span>
              <button
                type='button'
                className='btn btn-sm btn-outline-danger'
                onClick={() => setRows(rows.filter((_, k) => k !== j))}
              >
                Remove
              </button>
            </div>
            {field.itemFields?.map(f =>
              f.name === 'image' ? (
                <div key={f.name} className='mb-1'>
                  <label className='form-label mb-1 small'>
                    {f.name}
                    {f.required && ' *'}
                  </label>
                  <ImageUrlInput
                    value={String(row[f.name] ?? '')}
                    onChange={v => setRows(rows.map((r, k) => (k === j ? { ...r, [f.name]: v || undefined } : r)))}
                  />
                </div>
              ) : (
                <div key={f.name} className='mb-1'>
                  <label className='form-label mb-1 small'>
                    {f.name}
                    {f.required && ' *'}
                  </label>
                  <input
                    className='form-control form-control-sm'
                    value={String(row[f.name] ?? '')}
                    onChange={e => setRows(rows.map((r, k) => (k === j ? { ...r, [f.name]: e.target.value || undefined } : r)))}
                  />
                </div>
              ),
            )}
          </div>
        ))}
        <button type='button' className='btn btn-sm btn-outline-secondary' disabled={rows.length >= maxItems} onClick={() => setRows([...rows, {}])}>
          + Add row
        </button>
        <div className='form-text'>
          {field.minItems ?? 1}–{maxItems} rows
        </div>
      </div>
    );
  }

  return null;
};

// ----- preview (approximation of the customer renderer, degrade rule R1) -----

// Every preview fetch goes through these helpers: relative path, try/catch,
// non-2xx or malformed → null. The customer endpoints reach the backend
// through THIS admin gateway only if routed — a 404/401 simply R1-skips the
// component, matching the renderer contract (skip, never an error).
const safeJson = async (url: string, init?: RequestInit): Promise<unknown> => {
  try {
    const resp = await fetch(url, init);
    if (!resp.ok) return null;
    return await resp.json();
  } catch {
    return null;
  }
};

/** null = loading, [] = R1 skip, non-empty = render. */
function useR1List<T>(load: () => Promise<T[]>, depsKey: string): T[] | null {
  const [items, setItems] = React.useState<T[] | null>(null);
  React.useEffect(() => {
    let alive = true;
    setItems(null);
    load()
      .then(r => {
        if (alive) setItems(Array.isArray(r) ? r : []);
      })
      .catch(() => {
        if (alive) setItems([]);
      });
    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [depsKey]);
  return items;
}

const previewSection = (title: unknown, children: React.ReactNode) => (
  <div className='mb-3'>
    {Boolean(title) && <div className='fw-semibold small mb-1'>{String(title)}</div>}
    {children}
  </div>
);

interface GoodsCard {
  id?: number;
  name?: string;
  picUrl?: string;
  retailPrice?: number;
}

const normalizeGoods = (g: unknown): GoodsCard => {
  const o = (g ?? {}) as Record<string, unknown>;
  const inner = (o.goods ?? o) as Record<string, unknown>;
  return {
    id: (inner.id ?? o.id) as number | undefined,
    name: (inner.name ?? o.name) as string | undefined,
    picUrl: (inner.picUrl ?? o.picUrl) as string | undefined,
    retailPrice: (inner.retailPrice ?? o.retailPrice) as number | undefined,
  };
};

const GoodsCards: React.FC<{ goods: GoodsCard[] }> = ({ goods }) => (
  <div className='d-flex flex-wrap gap-2'>
    {goods.map((g, i) => (
      <div key={g.id ?? i} className='border rounded p-1 text-center' style={{ width: 104 }}>
        {g.picUrl ? (
          <img src={g.picUrl} alt={g.name ?? ''} style={{ width: 96, height: 96, objectFit: 'cover' }} />
        ) : (
          <div style={{ width: 96, height: 96, background: '#eee' }} />
        )}
        <div className='small text-truncate'>{g.name ?? `#${g.id}`}</div>
        {g.retailPrice != null && <div className='small text-danger'>¥{Number(g.retailPrice).toFixed(2)}</div>}
      </div>
    ))}
  </div>
);

const GoodsListPreview: React.FC<{ config: Record<string, unknown> }> = ({ config }) => {
  const depsKey = JSON.stringify(config);
  const items = useR1List<GoodsCard>(async () => {
    const mode = config.mode as string | undefined;
    const limit = Number(config.limit ?? 8);
    if (mode === 'byIds') {
      const ids = Array.isArray(config.goodsIds) ? (config.goodsIds as number[]) : [];
      if (!ids.length) return [];
      // Raw Map<goodsId, aggregate> — NO {errno,data} wrapper; missing ids are
      // simply absent. Preserve the configured order.
      const map = await safeJson('/srv/goods/batch', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(ids),
      });
      if (!map || typeof map !== 'object' || Array.isArray(map)) return [];
      const m = map as Record<string, unknown>;
      return ids.filter(gid => m[String(gid)] != null).map(gid => normalizeGoods(m[String(gid)]));
    }
    let qs: string;
    if (mode === 'byCategory') {
      if (!config.categoryId) return [];
      qs = `categoryId=${Number(config.categoryId)}&limit=${limit}&page=1`;
    } else if (mode === 'hot') {
      qs = `isHot=true&limit=${limit}&page=1`;
    } else if (mode === 'new') {
      qs = `isNew=true&limit=${limit}&page=1`;
    } else {
      return [];
    }
    const env = (await safeJson(`/srv/goods/list?${qs}`)) as { errno?: number; data?: { list?: unknown[] } } | null;
    if (!env || env.errno !== 0) return [];
    return (env.data?.list ?? []).map(normalizeGoods);
  }, depsKey);

  if (!items || items.length === 0) return null; // R1 skip
  return previewSection(config.title, <GoodsCards goods={items} />);
};

const StripPreview: React.FC<{ config: Record<string, unknown>; url: string; render: (item: Record<string, unknown>, i: number) => React.ReactNode }> = ({
  config,
  url,
  render,
}) => {
  const depsKey = `${url}|${JSON.stringify(config)}`;
  // BARE JSON ARRAY, no limit param on the endpoint — sliced client-side.
  const items = useR1List<Record<string, unknown>>(async () => {
    const arr = await safeJson(url);
    if (!Array.isArray(arr)) return [];
    return (arr as Record<string, unknown>[]).slice(0, Number(config.limit ?? 3));
  }, depsKey);

  if (!items || items.length === 0) return null; // R1 skip
  return previewSection(config.title, <div className='d-flex flex-wrap gap-2'>{items.map(render)}</div>);
};

const ArticleStripPreview: React.FC<{ config: Record<string, unknown> }> = ({ config }) => {
  const depsKey = JSON.stringify(config);
  const items = useR1List<Record<string, unknown>>(async () => {
    const limit = Number(config.limit ?? 3);
    const hot = config.hotOnly ? '&hotOnly=true' : '';
    const env = (await safeJson(`/srv/article/list?page=1&limit=${limit}${hot}`)) as { errno?: number; data?: { list?: Record<string, unknown>[] } } | null;
    if (!env || env.errno !== 0) return [];
    return env.data?.list ?? [];
  }, depsKey);

  if (!items || items.length === 0) return null; // R1 skip
  return previewSection(
    config.title,
    <ul className='list-unstyled mb-0'>
      {items.map((a, i) => (
        <li key={(a.id as number) ?? i} className='small border-bottom py-1'>
          {String(a.title ?? '')}
        </li>
      ))}
    </ul>,
  );
};

const PreviewComponent: React.FC<{ comp: EditorComponent }> = ({ comp }) => {
  const cfg = comp.config;
  switch (comp.type) {
    case 'banner':
      if (!cfg.image) return null;
      return (
        <div className='position-relative mb-3'>
          <img src={String(cfg.image)} alt={String(cfg.title ?? '')} style={{ width: '100%', maxHeight: 180, objectFit: 'cover' }} />
          {Boolean(cfg.title) && (
            <div className='position-absolute bottom-0 start-0 text-white px-2 py-1 small' style={{ background: 'rgba(0,0,0,.5)' }}>
              {String(cfg.title)}
            </div>
          )}
        </div>
      );
    case 'image-row': {
      const images = Array.isArray(cfg.images) ? (cfg.images as Record<string, unknown>[]) : [];
      const withSrc = images.filter(im => im?.image);
      if (!withSrc.length) return null;
      return (
        <div className='d-flex gap-1 mb-3'>
          {withSrc.map((im, i) => (
            <img key={i} src={String(im.image)} alt='' style={{ flex: 1, minWidth: 0, height: 80, objectFit: 'cover' }} />
          ))}
        </div>
      );
    }
    case 'rich-text':
      if (!cfg.html) return null;
      // Persisted html is server-sanitized (jsoup clean-and-store); while the
      // draft is unsaved this is the admin's own just-typed markup.
      return <div className='mb-3 small' dangerouslySetInnerHTML={{ __html: String(cfg.html) }} />;
    case 'goods-list':
      return <GoodsListPreview config={cfg} />;
    case 'coupon-strip':
      return (
        <StripPreview
          config={cfg}
          url='/srv/promotion/coupon/available'
          render={(c, i) => (
            <div key={(c.id as number) ?? i} className='border border-danger rounded px-2 py-1 small text-danger'>
              {String(c.name ?? c.title ?? 'Coupon')}
              {c.discount != null && <span className='ms-1 fw-semibold'>-¥{String(c.discount)}</span>}
              {c.min != null && <span className='text-muted ms-1'>over ¥{String(c.min)}</span>}
            </div>
          )}
        />
      );
    case 'seckill-strip':
      return (
        <StripPreview
          config={cfg}
          url='/srv/promotion/seckill/active'
          render={(s, i) => (
            <div key={(s.id as number) ?? i} className='border rounded px-2 py-1 small'>
              {String(s.goodsName ?? s.name ?? 'Seckill')}
              {(s.seckillPrice ?? s.price) != null && <span className='text-danger ms-1'>¥{String(s.seckillPrice ?? s.price)}</span>}
            </div>
          )}
        />
      );
    case 'article-strip':
      return <ArticleStripPreview config={cfg} />;
    default:
      return null;
  }
};

// ----- the editor ------------------------------------------------------------

const PageEditor: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const isEdit = Boolean(id);
  const navigate = useNavigate();

  const { data: palette, isLoading: paletteLoading, isError: paletteError } = useGetPagePaletteQuery();
  const { data: existing, isLoading: pageLoading } = useReadPageQuery(id as string, { skip: !isEdit });
  const [createPage, { isLoading: creating }] = useCreatePageMutation();
  const [updatePage, { isLoading: updating }] = useUpdatePageMutation();

  const [name, setName] = React.useState('');
  const [position, setPosition] = React.useState<PagePosition>('custom');
  const [components, setComponents] = React.useState<EditorComponent[]>([]);
  const [collapsed, setCollapsed] = React.useState<Record<string, boolean>>({});
  const [addType, setAddType] = React.useState('');
  const [showPreview, setShowPreview] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [saved, setSaved] = React.useState(false);
  const seededId = React.useRef<number | null>(null);

  // Seed from the server row exactly once per page id.
  React.useEffect(() => {
    if (!isEdit || !existing || existing.id == null || seededId.current === existing.id) return;
    seededId.current = existing.id;
    setName(existing.name ?? '');
    setPosition(existing.position ?? 'custom');
    const comps = (existing.config?.components ?? []).map(c => ({
      type: c.type,
      key: c.key || makeKey(c.type),
      config: (c.config ?? {}) as Record<string, unknown>,
    }));
    setComponents(comps);
    setCollapsed(Object.fromEntries(comps.map(c => [c.key, true])));
  }, [isEdit, existing]);

  const schemaByType = React.useMemo(() => {
    const m: Record<string, IPaletteComponent> = {};
    (palette as IPalette | undefined)?.components.forEach(c => {
      m[c.type] = c;
    });
    return m;
  }, [palette]);

  const maxComponents = palette?.maxComponents ?? 30;

  const addComponent = () => {
    if (!addType || components.length >= maxComponents) return;
    const comp: EditorComponent = { type: addType, key: makeKey(addType), config: {} };
    setComponents(prev => [...prev, comp]);
    setCollapsed(prev => ({ ...prev, [comp.key]: false }));
  };

  const move = (index: number, delta: number) => {
    setComponents(prev => {
      const next = [...prev];
      const target = index + delta;
      if (target < 0 || target >= next.length) return prev;
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });
  };

  const remove = (index: number) => setComponents(prev => prev.filter((_, i) => i !== index));

  const setConfig = (index: number, fieldName: string, value: unknown) => {
    setComponents(prev =>
      prev.map((c, i) => {
        if (i !== index) return c;
        const config = { ...c.config };
        if (value === undefined) delete config[fieldName];
        else config[fieldName] = value;
        return { ...c, config };
      }),
    );
  };

  const issues = React.useMemo(
    () => components.flatMap((c, i) => softIssues(c, schemaByType[c.type], i)),
    [components, schemaByType],
  );

  const onSave = async () => {
    setError(null);
    setSaved(false);
    if (!name.trim()) {
      setError('Page name is required.');
      return;
    }
    const config = {
      version: 1 as const,
      components: components.map(c => ({ type: c.type, key: c.key, config: c.config })),
    };
    if (isEdit) {
      const res = await updatePage({ id: Number(id), name: name.trim(), config });
      // errno 640 errmsg names the offending component — shown verbatim.
      const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
      if (msg) {
        setError(msg);
        return;
      }
      setSaved(true);
    } else {
      const res = await createPage({ name: name.trim(), position, config });
      const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
      if (msg) {
        setError(msg);
        return;
      }
      const createdId = 'data' in res ? res.data?.data?.id : undefined;
      if (createdId != null) navigate(`/admin/mall/page/${createdId}`, { replace: true });
      else navigate('/admin/mall/page');
    }
  };

  if (paletteLoading || (isEdit && pageLoading)) {
    return (
      <div className='app-container text-center p-5'>
        <span className='spinner-border text-primary' role='status' />
      </div>
    );
  }

  if (paletteError || !palette) {
    return (
      <div className='app-container'>
        <div className='alert alert-danger'>Failed to load the component palette — the editor cannot be built without it.</div>
      </div>
    );
  }

  const busy = creating || updating;

  return (
    <div className='app-container'>
      <div className='d-flex align-items-center justify-content-between mb-3'>
        <h5 className='mb-0'>{isEdit ? `Edit page #${id}` : 'New page'}</h5>
        <div>
          <button type='button' className={`btn btn-sm me-2 ${showPreview ? 'btn-info' : 'btn-outline-info'}`} onClick={() => setShowPreview(v => !v)}>
            {showPreview ? 'Hide preview' : 'Show preview'}
          </button>
          <button type='button' className='btn btn-sm btn-primary me-2' disabled={busy} onClick={onSave}>
            {busy ? 'Saving…' : isEdit ? 'Save changes' : 'Create draft'}
          </button>
          <button type='button' className='btn btn-sm btn-outline-secondary' onClick={() => navigate('/admin/mall/page')}>
            Back to list
          </button>
        </div>
      </div>

      {error && <div className='alert alert-danger'>{error}</div>}
      {saved && <div className='alert alert-success'>Saved. {existing?.status !== 'active' && 'The page stays a draft until activated from the list.'}</div>}
      {issues.length > 0 && (
        <div className='alert alert-warning'>
          <div className='fw-semibold small mb-1'>Validation hints (the server will refuse the save with the exact reason):</div>
          <ul className='mb-0 small'>
            {issues.map((msg, i) => (
              <li key={i}>{msg}</li>
            ))}
          </ul>
        </div>
      )}

      <div className='row mb-3' style={{ maxWidth: 720 }}>
        <div className='col-8'>
          <label className='form-label'>Page name *</label>
          <input className='form-control' maxLength={63} value={name} onChange={e => setName(e.target.value)} />
        </div>
        <div className='col-4'>
          <label className='form-label'>Position</label>
          <select
            className='form-select'
            value={position}
            disabled={isEdit}
            onChange={e => setPosition(e.target.value as PagePosition)}
            aria-label='Position'
          >
            <option value='custom'>custom</option>
            <option value='home'>home</option>
          </select>
          {isEdit ? (
            <div className='form-text'>Position is immutable after create.</div>
          ) : (
            <div className='form-text'>Only one home page can be active at a time.</div>
          )}
        </div>
      </div>

      <div className='row'>
        {/* --------------- component list + generated forms --------------- */}
        <div className={showPreview ? 'col-7' : 'col-12'} style={showPreview ? undefined : { maxWidth: 860 }}>
          <div className='d-flex align-items-center gap-2 mb-3'>
            <select className='form-select' style={{ width: 280 }} value={addType} onChange={e => setAddType(e.target.value)} aria-label='Component type'>
              <option value=''>Add component…</option>
              {palette.components.map(c => (
                <option key={c.type} value={c.type}>
                  {c.type} — {c.label}
                </option>
              ))}
            </select>
            <button type='button' className='btn btn-outline-primary' disabled={!addType || components.length >= maxComponents} onClick={addComponent}>
              + Add
            </button>
            <span className='text-muted small'>
              {components.length} / {maxComponents} components
            </span>
          </div>

          {components.length === 0 && <div className='text-muted py-4'>No components yet — add one above. Render order = list order.</div>}

          {components.map((comp, index) => {
            const schema = schemaByType[comp.type];
            const isCollapsed = collapsed[comp.key] ?? false;
            return (
              <div key={comp.key} className='card mb-2'>
                <div className='card-header d-flex align-items-center py-2'>
                  <span className='me-2 text-muted small'>#{index + 1}</span>
                  <Tag tag='primary'>{comp.type}</Tag>
                  <span className='ms-2 text-muted small text-truncate' style={{ maxWidth: 260 }}>
                    {summarize(comp)}
                  </span>
                  <span className='ms-auto'>
                    <button type='button' className='btn btn-sm btn-outline-secondary me-1' disabled={index === 0} onClick={() => move(index, -1)}>
                      Up
                    </button>
                    <button
                      type='button'
                      className='btn btn-sm btn-outline-secondary me-1'
                      disabled={index === components.length - 1}
                      onClick={() => move(index, 1)}
                    >
                      Down
                    </button>
                    <button
                      type='button'
                      className='btn btn-sm btn-outline-secondary me-1'
                      onClick={() => setCollapsed(prev => ({ ...prev, [comp.key]: !isCollapsed }))}
                    >
                      {isCollapsed ? 'Edit' : 'Collapse'}
                    </button>
                    <button type='button' className='btn btn-sm btn-outline-danger' onClick={() => remove(index)}>
                      Remove
                    </button>
                  </span>
                </div>
                {!isCollapsed && (
                  <div className='card-body py-2'>
                    {schema ? (
                      schema.fields
                        .filter(f => !f.requiredWhen || condMet(f, comp.config))
                        .map(f => <SchemaField key={f.name} compKey={comp.key} field={f} config={comp.config} onChange={(n, v) => setConfig(index, n, v)} />)
                    ) : (
                      <div className='text-danger small'>Unknown component type (not in the palette).</div>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>

        {/* --------------------------- preview ---------------------------- */}
        {showPreview && (
          <div className='col-5'>
            <div className='border rounded p-3' style={{ position: 'sticky', top: 8, maxHeight: '80vh', overflowY: 'auto' }}>
              <div className='d-flex align-items-center mb-2'>
                <span className='fw-semibold small'>Draft preview (approximation)</span>
                {busy && <Spinner />}
              </div>
              <div className='text-muted small mb-3'>
                Dynamic components resolve through the customer endpoints; per degrade rule R1 a component whose data fails to load or is empty is skipped
                silently — exactly like the customer renderer.
              </div>
              {components.length === 0 ? (
                <div className='text-muted small'>Nothing to preview.</div>
              ) : (
                components.map(comp => <PreviewComponent key={comp.key} comp={comp} />)
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default PageEditor;
