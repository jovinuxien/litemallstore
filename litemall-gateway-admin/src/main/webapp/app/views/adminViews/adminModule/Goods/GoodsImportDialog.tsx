import {
  BatchCreateResult,
  GoodsAllinone,
  useBatchCreateGoodsMutation,
  useGetCatAndBrandQuery,
} from 'app/shared/reducers/private/services/admingoodsrv/adminGoodsApi';
import * as React from 'react';
import { Button, Modal } from 'react-bootstrap';
import * as XLSX from 'xlsx';

// Bulk add / import dialog for goods. Accepts .xlsx / .csv (parsed client-side
// with SheetJS) and .json (either flat template rows, or full GoodsAllinone[]
// for power users), shows the rows in an editable preview grid (rows can also
// be added by hand — that IS the "bulk add" path), validates, then submits to
// POST /srv/private/admin/goods/batch-create and reports per-row results.
//
// Flat template columns: name, goodsSn, categoryId (or categoryName),
// brandName, picUrl, counterPrice, retailPrice, stock, brief, keywords,
// isOnSale. Each flat row becomes one goods with a single default SKU.

interface ImportRow {
  name: string;
  goodsSn: string;
  categoryId: string;
  categoryName: string;
  brandName: string;
  picUrl: string;
  counterPrice: string;
  retailPrice: string;
  stock: string;
  brief: string;
  keywords: string;
  isOnSale: boolean;
}

const EMPTY_ROW: ImportRow = {
  name: '',
  goodsSn: '',
  categoryId: '',
  categoryName: '',
  brandName: '',
  picUrl: '',
  counterPrice: '',
  retailPrice: '',
  stock: '0',
  brief: '',
  keywords: '',
  isOnSale: true,
};

const TEMPLATE_ROW = {
  name: 'Example goods',
  goodsSn: 'SN-0001',
  categoryId: '',
  categoryName: '',
  brandName: '',
  picUrl: 'https://example.com/image.jpg',
  counterPrice: 19.99,
  retailPrice: 9.99,
  stock: 100,
  brief: 'Short description',
  keywords: 'example,demo',
  isOnSale: true,
};

const str = (v: unknown): string => (v == null ? '' : String(v).trim());
const bool = (v: unknown): boolean => {
  if (typeof v === 'boolean') return v;
  const s = str(v).toLowerCase();
  return s === '' || s === 'true' || s === '1' || s === 'yes' || s === 'y';
};

const toImportRow = (r: Record<string, unknown>): ImportRow => ({
  name: str(r.name),
  goodsSn: str(r.goodsSn ?? r.goods_sn ?? r.sn),
  categoryId: str(r.categoryId ?? r.category_id),
  categoryName: str(r.categoryName ?? r.category),
  brandName: str(r.brandName ?? r.brand),
  picUrl: str(r.picUrl ?? r.pic_url ?? r.image),
  counterPrice: str(r.counterPrice ?? r.counter_price),
  retailPrice: str(r.retailPrice ?? r.retail_price ?? r.price),
  stock: str(r.stock ?? r.number ?? '0'),
  brief: str(r.brief),
  keywords: str(r.keywords),
  isOnSale: bool(r.isOnSale ?? r.is_on_sale),
});

const rowProblem = (row: ImportRow): string | null => {
  if (!row.name.trim()) return 'name required';
  if (!row.goodsSn.trim()) return 'goodsSn required';
  const price = Number(row.retailPrice);
  if (row.retailPrice === '' || Number.isNaN(price) || price < 0) return 'invalid retailPrice';
  const stock = Number(row.stock || 0);
  if (Number.isNaN(stock) || stock < 0) return 'invalid stock';
  return null;
};

const GoodsImportDialog: React.FC<{ onClose: () => void }> = ({ onClose }) => {
  const { data: catAndBrand } = useGetCatAndBrandQuery();
  const [batchCreate, { isLoading: submitting }] = useBatchCreateGoodsMutation();

  const [rows, setRows] = React.useState<ImportRow[]>([{ ...EMPTY_ROW }]);
  const [rawAllinone, setRawAllinone] = React.useState<GoodsAllinone[] | null>(null);
  const [fileName, setFileName] = React.useState<string | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [result, setResult] = React.useState<BatchCreateResult | null>(null);

  const setRow = (i: number, patch: Partial<ImportRow>) => setRows(prev => prev.map((row, idx) => (idx === i ? { ...row, ...patch } : row)));

  // brandName / categoryName resolution against the backend pick lists.
  const brandIdByName = React.useMemo(() => {
    const map = new Map<string, number>();
    (catAndBrand?.brandList ?? []).forEach(b => map.set(b.label.toLowerCase(), b.value));
    return map;
  }, [catAndBrand]);
  const categoryIdByName = React.useMemo(() => {
    const map = new Map<string, number>();
    (catAndBrand?.categoryList ?? []).forEach(l1 => {
      map.set(l1.label.toLowerCase(), l1.value);
      (l1.children ?? []).forEach(l2 => map.set(l2.label.toLowerCase(), l2.value));
    });
    return map;
  }, [catAndBrand]);

  const onFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setError(null);
    setResult(null);
    setRawAllinone(null);
    setFileName(file.name);
    try {
      if (/\.json$/i.test(file.name)) {
        const parsed = JSON.parse(await file.text());
        if (!Array.isArray(parsed) || parsed.length === 0) throw new Error('JSON must be a non-empty array');
        if (parsed[0] && typeof parsed[0] === 'object' && 'goods' in parsed[0]) {
          // Full GoodsAllinone[] — submit verbatim, preview read-only summary rows.
          setRawAllinone(parsed as GoodsAllinone[]);
          setRows(
            (parsed as GoodsAllinone[]).map(item =>
              toImportRow({
                ...(item.goods ?? {}),
                retailPrice: (item.products?.[0] as { price?: unknown })?.price,
                stock: (item.products?.[0] as { number?: unknown })?.number,
              })
            )
          );
        } else {
          setRows((parsed as Record<string, unknown>[]).map(toImportRow));
        }
      } else if (/\.(xlsx|xls|csv)$/i.test(file.name)) {
        const workbook = XLSX.read(await file.arrayBuffer(), { type: 'array' });
        const sheet = workbook.Sheets[workbook.SheetNames[0]];
        const parsed = XLSX.utils.sheet_to_json<Record<string, unknown>>(sheet, { defval: '' });
        if (parsed.length === 0) throw new Error('The file has no data rows');
        setRows(parsed.map(toImportRow));
      } else {
        throw new Error('Unsupported file type — use .xlsx, .csv or .json');
      }
    } catch (err) {
      setFileName(null);
      setError(`Could not parse file: ${(err as Error).message ?? err}`);
    }
  };

  const downloadTemplate = () => {
    const sheet = XLSX.utils.json_to_sheet([TEMPLATE_ROW]);
    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, sheet, 'goods');
    const buffer = XLSX.write(workbook, { bookType: 'xlsx', type: 'array' }) as ArrayBuffer;
    const url = URL.createObjectURL(new Blob([buffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = 'goods-import-template.xlsx';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  const problems = rows.map(rowProblem);
  const validCount = problems.filter(p => p == null).length;

  const onSubmit = async () => {
    setError(null);
    setResult(null);
    let body: GoodsAllinone[];
    if (rawAllinone) {
      body = rawAllinone;
    } else {
      if (rows.length === 0) {
        setError('Nothing to import.');
        return;
      }
      const firstProblem = problems.findIndex(p => p != null);
      if (firstProblem >= 0) {
        setError(`Row ${firstProblem + 1}: ${problems[firstProblem]}.`);
        return;
      }
      body = rows.map(row => {
        const categoryId = row.categoryId !== '' ? Number(row.categoryId) || 0 : categoryIdByName.get(row.categoryName.toLowerCase()) ?? 0;
        const brandId = brandIdByName.get(row.brandName.toLowerCase()) ?? 0;
        return {
          goods: {
            name: row.name.trim(),
            goodsSn: row.goodsSn.trim(),
            categoryId,
            brandId,
            picUrl: row.picUrl.trim(),
            gallery: row.picUrl.trim() ? [row.picUrl.trim()] : [],
            counterPrice: Number(row.counterPrice) || 0,
            brief: row.brief.trim(),
            keywords: row.keywords.trim(),
            isOnSale: row.isOnSale,
            isNew: true,
            isHot: false,
            unit: '件',
            sortOrder: 100,
            detail: '',
          },
          specifications: [{ specification: 'Specification', value: 'Standard', picUrl: '' }],
          products: [
            {
              specifications: ['Standard'],
              price: Number(row.retailPrice),
              number: Number(row.stock || 0),
              url: row.picUrl.trim(),
            },
          ],
          attributes: [],
        };
      });
    }
    const res = await batchCreate(body);
    if (!('data' in res) || !res.data) {
      setError('Request failed.');
      return;
    }
    if (res.data.errno !== 0) {
      setError(res.data.errmsg || `Import failed (errno ${res.data.errno}).`);
      return;
    }
    setResult(res.data.data);
  };

  const editable = rawAllinone == null;

  return (
    <Modal show onHide={onClose} size='xl' backdrop='static'>
      <Modal.Header closeButton>
        <Modal.Title>Bulk add / import goods</Modal.Title>
      </Modal.Header>
      <Modal.Body>
        <div className='d-flex flex-wrap align-items-center gap-2 mb-3'>
          <label className='btn btn-outline-primary btn-sm mb-0'>
            Choose file (.xlsx / .csv / .json)
            <input type='file' accept='.xlsx,.xls,.csv,.json' hidden onChange={onFile} />
          </label>
          {fileName && <span className='small text-muted'>{fileName}</span>}
          <button type='button' className='btn btn-link btn-sm' onClick={downloadTemplate}>
            Download template
          </button>
          {editable && (
            <button type='button' className='btn btn-outline-success btn-sm ms-auto' onClick={() => setRows(prev => [...prev, { ...EMPTY_ROW }])}>
              + Add row
            </button>
          )}
        </div>

        {error && <div className='alert alert-danger'>{error}</div>}
        {result && (
          <div className={`alert ${result.failed.length ? 'alert-warning' : 'alert-success'}`}>
            Created {result.created} goods.
            {result.failed.length > 0 && (
              <ul className='mb-0 mt-1'>
                {result.failed.map(f => (
                  <li key={f.index}>
                    Row {f.index + 1} {f.name ? `(${f.name})` : ''}: {f.error}
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}
        {rawAllinone && <div className='alert alert-info'>Full-detail JSON loaded — {rawAllinone.length} goods will be submitted as-is (preview below is a summary).</div>}

        <div style={{ maxHeight: 420, overflow: 'auto' }}>
          <table className='el-table'>
            <thead>
              <tr>
                <th style={{ width: 28 }}>#</th>
                <th>Name *</th>
                <th>Goods SN *</th>
                <th>Category</th>
                <th>Brand</th>
                <th>Image URL</th>
                <th style={{ width: 110 }}>Counter €</th>
                <th style={{ width: 110 }}>Price € *</th>
                <th style={{ width: 90 }}>Stock</th>
                <th style={{ width: 70 }}>On sale</th>
                {editable && <th style={{ width: 46 }} />}
              </tr>
            </thead>
            <tbody>
              {rows.map((row, i) => {
                const problem = problems[i];
                return (
                  <tr key={i} className={problem ? 'table-danger' : undefined} title={problem ?? undefined}>
                    <td className='text-muted small'>{i + 1}</td>
                    <td>
                      {editable ? (
                        <input className='form-control form-control-sm' value={row.name} onChange={e => setRow(i, { name: e.target.value })} />
                      ) : (
                        row.name
                      )}
                    </td>
                    <td>
                      {editable ? (
                        <input className='form-control form-control-sm' value={row.goodsSn} onChange={e => setRow(i, { goodsSn: e.target.value })} />
                      ) : (
                        row.goodsSn
                      )}
                    </td>
                    <td>
                      {editable ? (
                        <input
                          className='form-control form-control-sm'
                          placeholder='id or name'
                          value={row.categoryId || row.categoryName}
                          onChange={e =>
                            /^\d*$/.test(e.target.value.trim())
                              ? setRow(i, { categoryId: e.target.value.trim(), categoryName: '' })
                              : setRow(i, { categoryName: e.target.value, categoryId: '' })
                          }
                        />
                      ) : (
                        row.categoryId || row.categoryName || '—'
                      )}
                    </td>
                    <td>
                      {editable ? (
                        <input className='form-control form-control-sm' value={row.brandName} onChange={e => setRow(i, { brandName: e.target.value })} />
                      ) : (
                        row.brandName || '—'
                      )}
                    </td>
                    <td>
                      {editable ? (
                        <input className='form-control form-control-sm' value={row.picUrl} onChange={e => setRow(i, { picUrl: e.target.value })} />
                      ) : (
                        <span className='small text-muted'>{row.picUrl || '—'}</span>
                      )}
                    </td>
                    <td>
                      {editable ? (
                        <input
                          className='form-control form-control-sm'
                          type='number'
                          step='0.01'
                          min='0'
                          value={row.counterPrice}
                          onChange={e => setRow(i, { counterPrice: e.target.value })}
                        />
                      ) : (
                        row.counterPrice
                      )}
                    </td>
                    <td>
                      {editable ? (
                        <input
                          className='form-control form-control-sm'
                          type='number'
                          step='0.01'
                          min='0'
                          value={row.retailPrice}
                          onChange={e => setRow(i, { retailPrice: e.target.value })}
                        />
                      ) : (
                        row.retailPrice
                      )}
                    </td>
                    <td>
                      {editable ? (
                        <input
                          className='form-control form-control-sm'
                          type='number'
                          min='0'
                          value={row.stock}
                          onChange={e => setRow(i, { stock: e.target.value })}
                        />
                      ) : (
                        row.stock
                      )}
                    </td>
                    <td className='text-center'>
                      <input
                        className='form-check-input'
                        type='checkbox'
                        checked={row.isOnSale}
                        disabled={!editable}
                        onChange={e => setRow(i, { isOnSale: e.target.checked })}
                      />
                    </td>
                    {editable && (
                      <td className='text-end'>
                        <button
                          type='button'
                          className='btn btn-sm btn-outline-danger'
                          disabled={rows.length <= 1}
                          onClick={() => setRows(prev => prev.filter((_, idx) => idx !== i))}
                        >
                          ×
                        </button>
                      </td>
                    )}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        <div className='small text-muted mt-2'>
          {rows.length} row{rows.length === 1 ? '' : 's'}, {validCount} valid. Each row becomes one goods with a single default SKU; brand/category
          names are matched against the existing pick lists (unmatched → none).
        </div>
      </Modal.Body>
      <Modal.Footer>
        <Button variant='outline-secondary' onClick={onClose}>
          {result ? 'Close' : 'Cancel'}
        </Button>
        <Button variant='primary' disabled={submitting || rows.length === 0 || Boolean(result)} onClick={onSubmit}>
          {submitting ? 'Importing…' : `Import ${rawAllinone ? rawAllinone.length : rows.length} goods`}
        </Button>
      </Modal.Footer>
    </Modal>
  );
};

export default GoodsImportDialog;
