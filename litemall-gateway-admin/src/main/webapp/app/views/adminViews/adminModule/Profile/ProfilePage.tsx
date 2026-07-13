import {
  INoticeDetail,
  INoticeInboxRow,
  useChangePasswordMutation,
  useListNoticeInboxQuery,
  useMarkNoticesReadMutation,
  useReadNoticeMutation,
  useRemoveNoticeMutation,
  useRemoveNoticesMutation,
} from 'app/shared/reducers/private/services/adminParityApi';
import { fromServerDateTime } from 'app/shared/util/server-datetime';
import { errnoMessage, PAGE_SIZES, Pagination, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { Button, Modal } from 'react-bootstrap';

// Admin profile page — (a) change password (edge POST /profile/password:
// errno 605 wrong old password, 602 too short) and (b) the notice inbox
// (litemall_notice_admin rows via /profile/{lsnotice,catnotice,bcatnotice,
// rmnotice,brmnotice}). Opening a notice (catnotice, keyed by noticeId) marks
// it read; the batch/delete actions are keyed by the inbox-row id. All shapes
// live-verified against this gateway (2026-07-13).

const fmtTime = (value?: string) => (value ? String(value).replace('T', ' ').slice(0, 19) : '—');

// ----- Section (a): change password ---------------------------------------

const PasswordSection: React.FC = () => {
  const [changePassword, { isLoading: saving }] = useChangePasswordMutation();
  const [oldPassword, setOldPassword] = React.useState('');
  const [newPassword, setNewPassword] = React.useState('');
  const [confirmPassword, setConfirmPassword] = React.useState('');
  const [error, setError] = React.useState<string | null>(null);
  const [done, setDone] = React.useState(false);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setDone(false);
    if (!oldPassword || !newPassword) {
      setError('Both the current and the new password are required.');
      return;
    }
    if (newPassword !== confirmPassword) {
      setError('New password and confirmation do not match.');
      return;
    }
    const res = await changePassword({ oldPassword, newPassword });
    // errno 605 wrong old password, 602 new password too short (<6).
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) {
      setError(msg);
      return;
    }
    setDone(true);
    setOldPassword('');
    setNewPassword('');
    setConfirmPassword('');
  };

  return (
    <div className='mb-5'>
      <h5 className='mb-3'>Change password</h5>
      {error && <div className='alert alert-danger'>{error}</div>}
      {done && <div className='alert alert-success'>Password changed. The new password applies at your next login.</div>}
      <form onSubmit={onSubmit} style={{ maxWidth: 420 }}>
        <div className='mb-3'>
          <label className='form-label'>Current password</label>
          <input className='form-control' type='password' autoComplete='current-password' value={oldPassword} onChange={e => setOldPassword(e.target.value)} />
        </div>
        <div className='mb-3'>
          <label className='form-label'>New password</label>
          <input className='form-control' type='password' autoComplete='new-password' value={newPassword} onChange={e => setNewPassword(e.target.value)} />
          <div className='form-text'>At least 6 characters.</div>
        </div>
        <div className='mb-3'>
          <label className='form-label'>Confirm new password</label>
          <input className='form-control' type='password' autoComplete='new-password' value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} />
        </div>
        <button className='btn btn-primary' type='submit' disabled={saving}>
          {saving ? 'Saving…' : 'Change password'}
        </button>
      </form>
    </div>
  );
};

// ----- Section (b): notice inbox -------------------------------------------

const TABS: { value: 'all' | 'unread' | 'read'; label: string }[] = [
  { value: 'all', label: 'All' },
  { value: 'unread', label: 'Unread' },
  { value: 'read', label: 'Read' },
];

const NoticeInboxSection: React.FC = () => {
  const [type, setType] = React.useState<'all' | 'unread' | 'read'>('all');
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(10);
  const [titleInput, setTitleInput] = React.useState('');
  const [title, setTitle] = React.useState('');
  const [selected, setSelected] = React.useState<number[]>([]);
  const [actionError, setActionError] = React.useState<string | null>(null);

  const { data, isLoading, isFetching, isError, error } = useListNoticeInboxQuery({ type, title: title || undefined, page, limit });
  const [readNotice, { isLoading: opening }] = useReadNoticeMutation();
  const [markRead, { isLoading: marking }] = useMarkNoticesReadMutation();
  const [removeNotice, { isLoading: removing }] = useRemoveNoticeMutation();
  const [removeNotices, { isLoading: batchRemoving }] = useRemoveNoticesMutation();

  const [detail, setDetail] = React.useState<INoticeDetail | null>(null);

  const list = data?.list ?? [];
  const total = data?.total ?? 0;
  const pages = data?.pages ?? 0;
  const errStatus = (error as { status?: number | string })?.status;
  const busy = marking || removing || batchRemoving;

  // Drop selections that fell off the current page (tab/filter/page change).
  React.useEffect(() => {
    setSelected(prev => prev.filter(id => list.some(row => row.id === id)));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data]);

  const switchTab = (next: 'all' | 'unread' | 'read') => {
    setType(next);
    setPage(1);
  };

  const onSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(1);
    setTitle(titleInput.trim());
  };

  const toggle = (id?: number) => {
    if (id == null) return;
    setSelected(prev => (prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id]));
  };

  const pageIds = list.map(row => row.id).filter((id): id is number => id != null);
  const allChecked = pageIds.length > 0 && pageIds.every(id => selected.includes(id));
  const toggleAll = () => setSelected(allChecked ? [] : pageIds);

  // Opening a notice returns the body AND marks it read (keyed by noticeId).
  const onOpen = async (row: INoticeInboxRow) => {
    if (row.noticeId == null) return;
    setActionError(null);
    const res = await readNotice({ noticeId: row.noticeId });
    if (!('data' in res) || errnoMessage(res.data)) {
      setActionError(('data' in res && errnoMessage(res.data)) || 'Failed to open notice.');
      return;
    }
    const d = res.data.data ?? {};
    setDetail({ ...d, time: fromServerDateTime(d.time) ?? d.time });
  };

  const onDeleteRow = async (row: INoticeInboxRow) => {
    if (row.id == null || !window.confirm(`Delete notice "${row.noticeTitle ?? row.id}" from your inbox?`)) return;
    setActionError(null);
    const res = await removeNotice({ id: row.id });
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) setActionError(msg);
  };

  const onMarkSelectedRead = async () => {
    if (selected.length === 0) return;
    setActionError(null);
    const res = await markRead({ ids: selected });
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) setActionError(msg);
    else setSelected([]);
  };

  const onDeleteSelected = async () => {
    if (selected.length === 0) return;
    if (!window.confirm(`Delete ${selected.length} selected notice${selected.length === 1 ? '' : 's'} from your inbox?`)) return;
    setActionError(null);
    const res = await removeNotices({ ids: selected });
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) setActionError(msg);
    else setSelected([]);
  };

  return (
    <div>
      <h5 className='mb-3'>Notice inbox</h5>

      <ul className='nav nav-tabs mb-3'>
        {TABS.map(tab => (
          <li className='nav-item' key={tab.value}>
            <button type='button' className={`nav-link${type === tab.value ? ' active' : ''}`} onClick={() => switchTab(tab.value)}>
              {tab.label}
            </button>
          </li>
        ))}
      </ul>

      <form className='filter-container' onSubmit={onSearch}>
        <input
          className='form-control filter-item'
          style={{ width: 200 }}
          placeholder='Title'
          value={titleInput}
          onChange={e => setTitleInput(e.target.value)}
        />
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
        <button className='btn btn-outline-primary filter-item' type='button' disabled={selected.length === 0 || busy} onClick={onMarkSelectedRead}>
          Mark read{selected.length > 0 ? ` (${selected.length})` : ''}
        </button>
        <button className='btn btn-outline-danger filter-item' type='button' disabled={selected.length === 0 || busy} onClick={onDeleteSelected}>
          Delete{selected.length > 0 ? ` (${selected.length})` : ''}
        </button>
        {isFetching && <Spinner />}
      </form>

      {isError && <div className='alert alert-danger'>Failed to load notices{errStatus ? ` (${errStatus})` : ''}.</div>}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th style={{ width: 32 }}>
              <input type='checkbox' className='form-check-input' checked={allChecked} onChange={toggleAll} aria-label='Select all on page' />
            </th>
            <th>Title</th>
            <th>Status</th>
            <th>Received</th>
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
                No notices.
              </td>
            </tr>
          ) : (
            list.map(row => (
              <tr key={row.id}>
                <td>
                  <input
                    type='checkbox'
                    className='form-check-input'
                    checked={row.id != null && selected.includes(row.id)}
                    onChange={() => toggle(row.id)}
                    aria-label={`Select notice ${row.id}`}
                  />
                </td>
                <td>
                  <button
                    type='button'
                    className='btn btn-link btn-sm p-0 text-start'
                    style={{ fontWeight: row.readTime ? undefined : 600 }}
                    disabled={opening}
                    onClick={() => onOpen(row)}
                  >
                    {row.noticeTitle || `#${row.noticeId}`}
                  </button>
                </td>
                <td>
                  {row.readTime ? (
                    <span className='text-muted small'>read {fmtTime(row.readTime)}</span>
                  ) : (
                    <Tag tag='warning'>unread</Tag>
                  )}
                </td>
                <td className='text-muted small'>{fmtTime(row.addTime)}</td>
                <td className='text-end'>
                  <button className='btn btn-sm btn-outline-danger' disabled={busy} onClick={() => onDeleteRow(row)}>
                    Delete
                  </button>
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>

      <Pagination page={page} pages={pages} total={total} rowCount={list.length} limit={limit} busy={isFetching} onPage={setPage} />

      <Modal show={detail != null} onHide={() => setDetail(null)} centered>
        <Modal.Header closeButton>
          <Modal.Title as='h6'>{detail?.title || 'Notice'}</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <div className='text-muted small mb-2'>
            {detail?.admin ? `From ${detail.admin}` : 'System notice'}
            {detail?.time ? ` · ${fmtTime(detail.time)}` : ''}
          </div>
          <div style={{ whiteSpace: 'pre-wrap' }}>{detail?.content || <span className='text-muted'>(no content)</span>}</div>
        </Modal.Body>
        <Modal.Footer>
          <Button variant='outline-secondary' size='sm' onClick={() => setDetail(null)}>
            Close
          </Button>
        </Modal.Footer>
      </Modal>
    </div>
  );
};

const ProfilePage: React.FC = () => (
  <div className='app-container'>
    <PasswordSection />
    <NoticeInboxSection />
  </div>
);

export default ProfilePage;
