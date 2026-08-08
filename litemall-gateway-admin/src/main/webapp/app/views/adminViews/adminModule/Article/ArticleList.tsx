import {
  IArticle,
  IArticleCategory,
  useCreateArticleCategoryMutation,
  useDeleteArticleCategoryMutation,
  useDeleteArticleMutation,
  useListArticleCategoriesQuery,
  useListArticlesQuery,
  useUpdateArticleCategoryMutation,
} from 'app/shared/reducers/private/services/adminContentApi';
import { fromServerDateTime } from 'app/shared/util/server-datetime';
import { errnoMessage, PAGE_SIZES, Pagination, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { Link } from 'react-router-dom';

// Article CMS admin — two tabs: the article list (title/category/status
// filters, published/hidden badge, hot/banner badges, view count, edit/delete)
// and an inline category manager (name, sort order, per-category article
// count; add/edit/delete inline). Category delete is refused server-side while
// articles still reference it (errno 641) — the errmsg is surfaced inline.
// Contract: litemall-goods-management/docs/handoff-content-endpoints.md §1.

// Tolerates the raw array shape too — a data-shape surprise must never throw
// in render (a crash here trips the app-level ErrorBoundary for the session).
const fmtTime = (t?: unknown) => {
  const s = fromServerDateTime(t);
  return s ? s.replace('T', ' ').slice(0, 16) : '—';
};

// ----- Articles tab ---------------------------------------------------------

const ArticlesTab: React.FC = () => {
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(20);
  const [titleInput, setTitleInput] = React.useState('');
  const [title, setTitle] = React.useState('');
  const [categoryId, setCategoryId] = React.useState('');
  const [status, setStatus] = React.useState('');

  const { data, isLoading, isFetching, isError, error } = useListArticlesQuery({
    page,
    limit,
    title,
    categoryId: categoryId ? Number(categoryId) : undefined,
    status: status as '' | 'published' | 'hidden',
  });
  const { data: categories = [] } = useListArticleCategoriesQuery();
  const [deleteArticle, { isLoading: deleting }] = useDeleteArticleMutation();
  const [actionError, setActionError] = React.useState<string | null>(null);

  const list = data?.list ?? [];
  const total = data?.total ?? 0;
  const pages = limit > 0 ? Math.ceil(total / limit) : 0;
  const errStatus = (error as { status?: number | string })?.status;

  const onSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(1);
    setTitle(titleInput.trim());
  };

  const onDelete = async (article: IArticle) => {
    if (!window.confirm(`Delete article "${article.title ?? article.id}"?`)) return;
    setActionError(null);
    const res = await deleteArticle({ id: article.id as number });
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) setActionError(msg);
  };

  return (
    <>
      <form className='filter-container' onSubmit={onSearch}>
        <input
          className='form-control filter-item'
          style={{ width: 220 }}
          placeholder='Article title'
          value={titleInput}
          onChange={e => setTitleInput(e.target.value)}
        />
        <select
          className='form-select filter-item'
          style={{ width: 180 }}
          value={categoryId}
          onChange={e => {
            setPage(1);
            setCategoryId(e.target.value);
          }}
          aria-label='Category'
        >
          <option value=''>All categories</option>
          {categories.map(c => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
        <select
          className='form-select filter-item'
          style={{ width: 140 }}
          value={status}
          onChange={e => {
            setPage(1);
            setStatus(e.target.value);
          }}
          aria-label='Status'
        >
          <option value=''>All statuses</option>
          <option value='published'>Published</option>
          <option value='hidden'>Hidden</option>
        </select>
        <select
          className='form-select filter-item'
          style={{ width: 120 }}
          value={limit}
          onChange={e => {
            setPage(1);
            setLimit(Number(e.target.value));
          }}
          aria-label='Page size'
        >
          {PAGE_SIZES.map(n => (
            <option key={n} value={n}>
              {n} / page
            </option>
          ))}
        </select>
        <button className='btn btn-primary filter-item' type='submit'>
          Search
        </button>
        <Link className='btn btn-success filter-item' to='/admin/mall/article/create'>
          + New article
        </Link>
        {isFetching && <Spinner />}
      </form>

      {isError && <div className='alert alert-danger'>Failed to load articles{errStatus ? ` (${errStatus})` : ''}.</div>}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>Image</th>
            <th>Title</th>
            <th>Category</th>
            <th>Status</th>
            <th>Flags</th>
            <th className='text-end'>Views</th>
            <th>Added</th>
            <th className='text-end'>Actions</th>
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={8} className='text-center p-5'>
                <span className='spinner-border text-primary' role='status' />
              </td>
            </tr>
          ) : list.length === 0 ? (
            <tr>
              <td colSpan={8} className='text-center text-muted py-5'>
                No articles found.
              </td>
            </tr>
          ) : (
            list.map(a => (
              <tr key={a.id}>
                <td style={{ width: 56 }}>
                  {a.picUrl ? <img src={a.picUrl} alt={a.title ?? ''} className='cell-thumb' /> : <div className='cell-thumb' style={{ background: '#eee' }} />}
                </td>
                <td>
                  <Link to={`/admin/mall/article/${a.id}`}>{a.title || `#${a.id}`}</Link>
                  {a.summary && <div className='text-muted small text-truncate' style={{ maxWidth: 320 }}>{a.summary}</div>}
                </td>
                <td>{a.categoryName || <span className='text-muted'>—</span>}</td>
                <td>{a.status === 'published' ? <Tag tag='success'>published</Tag> : <Tag tag='info'>hidden</Tag>}</td>
                <td>
                  {a.isHot && (
                    <span className='me-1'>
                      <Tag tag='danger'>Hot</Tag>
                    </span>
                  )}
                  {a.isBanner && <Tag tag='warning'>Banner</Tag>}
                  {!a.isHot && !a.isBanner && <span className='text-muted'>—</span>}
                </td>
                <td className='text-end'>{a.viewCount ?? 0}</td>
                <td className='text-muted small'>{fmtTime(a.addTime)}</td>
                <td className='text-end'>
                  <Link to={`/admin/mall/article/${a.id}`} className='btn btn-sm btn-outline-primary me-1'>
                    Edit
                  </Link>
                  <button className='btn btn-sm btn-outline-danger' disabled={deleting} onClick={() => onDelete(a)}>
                    Delete
                  </button>
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>

      <Pagination page={page} pages={pages} total={total} rowCount={list.length} limit={limit} busy={isFetching} onPage={setPage} />
    </>
  );
};

// ----- Categories tab -------------------------------------------------------

const CategoriesTab: React.FC = () => {
  const { data: categories = [], isLoading, isFetching, isError } = useListArticleCategoriesQuery();
  const [createCategory, { isLoading: creating }] = useCreateArticleCategoryMutation();
  const [updateCategory, { isLoading: updating }] = useUpdateArticleCategoryMutation();
  const [deleteCategory, { isLoading: deleting }] = useDeleteArticleCategoryMutation();

  const [actionError, setActionError] = React.useState<string | null>(null);
  const [newName, setNewName] = React.useState('');
  const [newSort, setNewSort] = React.useState('0');
  const [editId, setEditId] = React.useState<number | null>(null);
  const [editName, setEditName] = React.useState('');
  const [editSort, setEditSort] = React.useState('0');

  const busy = creating || updating || deleting;

  const onAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newName.trim()) {
      setActionError('Category name is required.');
      return;
    }
    setActionError(null);
    const res = await createCategory({ name: newName.trim(), sortOrder: Number(newSort) || 0 });
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) {
      setActionError(msg);
      return;
    }
    setNewName('');
    setNewSort('0');
  };

  const startEdit = (c: IArticleCategory) => {
    setActionError(null);
    setEditId(c.id ?? null);
    setEditName(c.name ?? '');
    setEditSort(String(c.sortOrder ?? 0));
  };

  const onSaveEdit = async () => {
    if (editId == null) return;
    if (!editName.trim()) {
      setActionError('Category name is required.');
      return;
    }
    setActionError(null);
    const res = await updateCategory({ id: editId, name: editName.trim(), sortOrder: Number(editSort) || 0 });
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) {
      setActionError(msg);
      return;
    }
    setEditId(null);
  };

  // Server refuses while articles still reference the category (errno 641) —
  // the errmsg (which includes the reference count) is surfaced inline.
  const onDelete = async (c: IArticleCategory) => {
    if (!window.confirm(`Delete category "${c.name ?? c.id}"?`)) return;
    setActionError(null);
    const res = await deleteCategory({ id: c.id as number });
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) setActionError(msg);
  };

  return (
    <>
      <form className='filter-container' onSubmit={onAdd}>
        <input
          className='form-control filter-item'
          style={{ width: 220 }}
          placeholder='New category name'
          value={newName}
          onChange={e => setNewName(e.target.value)}
        />
        <input
          className='form-control filter-item'
          style={{ width: 120 }}
          type='number'
          placeholder='Sort order'
          value={newSort}
          onChange={e => setNewSort(e.target.value)}
          aria-label='Sort order'
        />
        <button className='btn btn-success filter-item' type='submit' disabled={busy}>
          + Add category
        </button>
        {isFetching && <Spinner />}
      </form>

      {isError && <div className='alert alert-danger'>Failed to load categories.</div>}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>Name</th>
            <th className='text-end'>Sort order</th>
            <th className='text-end'>Articles</th>
            <th className='text-end'>Actions</th>
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={4} className='text-center p-5'>
                <span className='spinner-border text-primary' role='status' />
              </td>
            </tr>
          ) : categories.length === 0 ? (
            <tr>
              <td colSpan={4} className='text-center text-muted py-5'>
                No categories yet.
              </td>
            </tr>
          ) : (
            categories.map(c =>
              editId === c.id ? (
                <tr key={c.id}>
                  <td>
                    <input className='form-control form-control-sm' value={editName} onChange={e => setEditName(e.target.value)} />
                  </td>
                  <td className='text-end'>
                    <input
                      className='form-control form-control-sm ms-auto'
                      style={{ width: 100 }}
                      type='number'
                      value={editSort}
                      onChange={e => setEditSort(e.target.value)}
                    />
                  </td>
                  <td className='text-end'>{c.articleCount ?? 0}</td>
                  <td className='text-end'>
                    <button className='btn btn-sm btn-primary me-1' disabled={busy} onClick={onSaveEdit}>
                      Save
                    </button>
                    <button className='btn btn-sm btn-outline-secondary' disabled={busy} onClick={() => setEditId(null)}>
                      Cancel
                    </button>
                  </td>
                </tr>
              ) : (
                <tr key={c.id}>
                  <td>{c.name}</td>
                  <td className='text-end'>{c.sortOrder ?? 0}</td>
                  <td className='text-end'>{c.articleCount ?? 0}</td>
                  <td className='text-end'>
                    <button className='btn btn-sm btn-outline-primary me-1' disabled={busy} onClick={() => startEdit(c)}>
                      Edit
                    </button>
                    <button className='btn btn-sm btn-outline-danger' disabled={busy} onClick={() => onDelete(c)}>
                      Delete
                    </button>
                  </td>
                </tr>
              ),
            )
          )}
        </tbody>
      </table>
    </>
  );
};

// ----- Page shell -----------------------------------------------------------

const ArticleList: React.FC = () => {
  const [tab, setTab] = React.useState<'articles' | 'categories'>('articles');

  return (
    <div className='app-container'>
      <div className='btn-group mb-3' role='tablist' aria-label='Article CMS sections'>
        <button
          type='button'
          role='tab'
          aria-selected={tab === 'articles'}
          className={`btn ${tab === 'articles' ? 'btn-primary' : 'btn-outline-primary'}`}
          onClick={() => setTab('articles')}
        >
          Articles
        </button>
        <button
          type='button'
          role='tab'
          aria-selected={tab === 'categories'}
          className={`btn ${tab === 'categories' ? 'btn-primary' : 'btn-outline-primary'}`}
          onClick={() => setTab('categories')}
        >
          Categories
        </button>
      </div>
      {tab === 'articles' ? <ArticlesTab /> : <CategoriesTab />}
    </div>
  );
};

export default ArticleList;
