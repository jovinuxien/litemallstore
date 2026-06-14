import { useGetAdminGoodsDetailQuery } from 'app/shared/reducers/private/services/admingoodsrv/adminGoodsApi';
import { ProductStatus } from 'app/shared/model/enumerations/product-status.model';
import React from 'react';
import { useNavigate, useParams } from 'react-router-dom';

// Per-item goods detail, styled to the upstream litemall-admin look: an
// .app-container of .box-card panels (overview, specifications, SKUs) with
// .el-tag pills and an .el-table SKU grid. Data flow unchanged — fetched per id
// as an authenticated admin via adminGoodsApi.

type ElTag = 'success' | 'danger' | 'warning' | 'info';

const STATUS_META: Record<string, { label: string; tag: ElTag }> = {
  [ProductStatus.ONSALE]: { label: 'On sale', tag: 'success' },
  [ProductStatus.OOUTOFSTOCK]: { label: 'Out of stock', tag: 'danger' },
  [ProductStatus.INREPLENISHMENT]: { label: 'Replenishing', tag: 'warning' },
  [ProductStatus.LOCKED]: { label: 'Locked', tag: 'info' },
};

const priceNum = (value: unknown): number => {
  if (value == null) return 0;
  if (typeof value === 'number') return value;
  const amount = (value as { amount?: unknown }).amount;
  return typeof amount === 'number' ? amount : 0;
};

const GoodsDetail: React.FC = () => {
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const { data, isLoading, isError, error } = useGetAdminGoodsDetailQuery(id as string, { skip: id == null });

  if (isLoading) {
    return (
      <div className='app-container text-center p-5'>
        <span className='spinner-border text-primary' role='status' />
      </div>
    );
  }

  const errStatus = (error as { status?: number | string })?.status;

  if (isError || !data) {
    return (
      <div className='app-container'>
        <button className='btn btn-link px-0 mb-2' onClick={() => navigate(-1)}>
          ← Back
        </button>
        <div className='alert alert-danger'>{isError ? `Failed to load goods${errStatus ? ` (${errStatus})` : ''}.` : 'Goods not found.'}</div>
      </div>
    );
  }

  const { goods, specificationList = [], products = [], brand, categoryNames = [] } = data;
  const status = (goods as unknown as { status?: ProductStatus }).status;
  const meta = status ? STATUS_META[status] : undefined;
  const gallery = goods?.gallery && goods.gallery.length > 0 ? goods.gallery : goods?.picUrl ? [goods.picUrl] : [];

  return (
    <div className='app-container goods-detail'>
      <button className='btn btn-link px-0 mb-2' onClick={() => navigate(-1)}>
        ← Back
      </button>

      <div className='box-card'>
        <div className='box-card-body'>
          <div className='row'>
            <div className='col-md-4'>
              {goods?.picUrl ? (
                <img src={goods.picUrl} alt={goods?.name ?? ''} className='img-fluid border rounded' />
              ) : (
                <div style={{ height: 240, background: '#eee' }} className='rounded' />
              )}
              {gallery.length > 1 && (
                <div className='d-flex flex-wrap gap-2 mt-2'>
                  {gallery.map((g, i) => (
                    <img key={`${g}-${i}`} src={g} alt={`view ${i + 1}`} style={{ width: 56, height: 56, objectFit: 'cover', borderRadius: 4 }} />
                  ))}
                </div>
              )}
            </div>

            <div className='col-md-8'>
              <div className='d-flex align-items-center gap-2 mb-1'>
                <h3 className='mb-0'>{goods?.name}</h3>
                {meta && <span className={`el-tag el-tag--${meta.tag}`}>{meta.label}</span>}
              </div>
              {goods?.brief && <p className='text-muted'>{goods.brief}</p>}
              <hr />
              <div className='row g-3'>
                <div className='col-6 col-sm-3'>
                  <div className='small text-muted'>Retail price</div>
                  <div className='h5'>¥{priceNum(goods?.retailPrice).toFixed(2)}</div>
                </div>
                <div className='col-6 col-sm-3'>
                  <div className='small text-muted'>Counter price</div>
                  <div className='h5'>¥{priceNum(goods?.counterPrice).toFixed(2)}</div>
                </div>
                <div className='col-6 col-sm-3'>
                  <div className='small text-muted'>Brand</div>
                  <div className='h5'>{brand || '—'}</div>
                </div>
                <div className='col-6 col-sm-3'>
                  <div className='small text-muted'>Unit</div>
                  <div className='h5'>{goods?.unit || '—'}</div>
                </div>
              </div>
              {categoryNames.length > 0 && (
                <div className='mt-3'>
                  <div className='small text-muted'>Categories</div>
                  <div className='d-flex flex-wrap gap-2 mt-1'>
                    {categoryNames.map((c, i) => (
                      <span key={`${c}-${i}`} className='el-tag el-tag--info'>
                        {c}
                      </span>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      {specificationList.length > 0 && (
        <div className='box-card'>
          <div className='box-card-header'>Specifications</div>
          <div className='box-card-body'>
            <ul className='mb-0'>
              {specificationList.map((spec, i) => (
                <li key={`${spec.name}-${i}`}>
                  <strong>{spec.name}:</strong> {(spec.valueList || []).map(v => v.value).join(', ')}
                </li>
              ))}
            </ul>
          </div>
        </div>
      )}

      <div className='box-card'>
        <div className='box-card-header'>SKUs</div>
        <div className='box-card-body'>
          <table className='el-table'>
            <thead>
              <tr>
                <th>Specifications</th>
                <th className='text-end'>Price</th>
                <th className='text-end'>Stock</th>
              </tr>
            </thead>
            <tbody>
              {products.length === 0 ? (
                <tr>
                  <td colSpan={3} className='text-center text-muted'>
                    No SKUs.
                  </td>
                </tr>
              ) : (
                products.map((p, i) => (
                  <tr key={p.id ?? i}>
                    <td>{(p.specifications || []).map(s => (s as unknown as { typeProduct?: string }).typeProduct ?? String(s)).join(' / ') || '—'}</td>
                    <td className='text-end'>¥{priceNum(p.price).toFixed(2)}</td>
                    <td className='text-end'>{p.number ?? 0}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

export default GoodsDetail;
