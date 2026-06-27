import { ICategory, ICategoryVo } from 'app/shared/model/admin/catalog.model';
import { useDeleteCategoryMutation, useListCategoriesQuery } from 'app/shared/reducers/private/services/adminCatalogApi';
import { errnoMessage, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { Link } from 'react-router-dom';

// Admin category taxonomy rendered as an L1 → L2 tree (the /category/list
// CategoryVo shape), with create/edit/delete and an "add subcategory" shortcut.
// Authenticated admin → /srv/private/admin/category. Not paged (full tree).

const CategoryList: React.FC = () => {
  const { data: tree, isLoading, isFetching, isError, error } = useListCategoriesQuery();
  const [deleteCategory, { isLoading: deleting }] = useDeleteCategoryMutation();
  const [actionError, setActionError] = React.useState<string | null>(null);

  const errStatus = (error as { status?: number | string })?.status;
  const list = tree ?? [];

  const onDelete = async (cat: ICategory) => {
    if (!window.confirm(`Delete category "${cat.name ?? cat.id}"? Subcategories are not removed automatically.`)) return;
    setActionError(null);
    const res = await deleteCategory({ id: cat.id });
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) setActionError(msg);
  };

  const Thumb: React.FC<{ url?: string }> = ({ url }) =>
    url ? <img src={url} alt='' className='cell-thumb' /> : <div className='cell-thumb' style={{ background: '#eee' }} />;

  const Actions: React.FC<{ cat: ICategoryVo }> = ({ cat }) => (
    <>
      <Link to={`/admin/mall/category/${cat.id}`} className='btn btn-sm btn-outline-primary me-1'>
        Edit
      </Link>
      <button className='btn btn-sm btn-outline-danger' disabled={deleting} onClick={() => onDelete(cat)}>
        Delete
      </button>
    </>
  );

  return (
    <div className='app-container'>
      <div className='filter-container'>
        <Link className='btn btn-success filter-item' to='/admin/mall/category/create'>
          + New L1 category
        </Link>
        {isFetching && <Spinner />}
      </div>

      {isError && <div className='alert alert-danger'>Failed to load categories{errStatus ? ` (${errStatus})` : ''}.</div>}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th style={{ width: 56 }}>Icon</th>
            <th>Name</th>
            <th>Level</th>
            <th>Keywords</th>
            <th className='text-end'>Actions</th>
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={5} className='text-center p-5'>
                <span className='spinner-border text-primary' role='status' />
              </td>
            </tr>
          ) : list.length === 0 ? (
            <tr>
              <td colSpan={5} className='text-center text-muted py-5'>
                No categories found.
              </td>
            </tr>
          ) : (
            list.flatMap(l1 => [
              <tr key={`l1-${l1.id}`}>
                <td>
                  <Thumb url={l1.iconUrl || l1.picUrl} />
                </td>
                <td>
                  <strong>
                    <Link to={`/admin/mall/category/${l1.id}`}>{l1.name || `#${l1.id}`}</Link>
                  </strong>
                </td>
                <td>
                  <Tag tag='info'>L1</Tag>
                </td>
                <td className='text-muted small'>{l1.keywords}</td>
                <td className='text-end'>
                  <Link to={`/admin/mall/category/create?pid=${l1.id}`} className='btn btn-sm btn-outline-success me-1'>
                    + Sub
                  </Link>
                  <Actions cat={l1} />
                </td>
              </tr>,
              ...(l1.children ?? []).map(l2 => (
                <tr key={`l2-${l2.id}`}>
                  <td>
                    <Thumb url={l2.iconUrl || l2.picUrl} />
                  </td>
                  <td style={{ paddingLeft: 32 }}>
                    <span className='text-muted me-1'>└</span>
                    <Link to={`/admin/mall/category/${l2.id}`}>{l2.name || `#${l2.id}`}</Link>
                  </td>
                  <td>
                    <Tag tag='primary'>L2</Tag>
                  </td>
                  <td className='text-muted small'>{l2.keywords}</td>
                  <td className='text-end'>
                    <Actions cat={l2} />
                  </td>
                </tr>
              )),
            ])
          )}
        </tbody>
      </table>
    </div>
  );
};

export default CategoryList;
