import React, { useCallback, useEffect, useState } from 'react';
import { Button, Spinner } from 'react-bootstrap';
import { Link, useNavigate } from 'react-router-dom';

import { IAddress, isMissingEndpoint, userApi } from 'app/shared/api';
import './user.scss';

/**
 * Address book, modelled on litemall-vue `user/module-address`. Lists saved
 * addresses with edit/delete and an add button. Sourced from `/srv/address/*`;
 * graceful empty when not live (follow-up: order/user worktree).
 */
const AddressList: React.FC = () => {
  const navigate = useNavigate();
  const [addresses, setAddresses] = useState<IAddress[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const list = await userApi.addressList();
      setAddresses(list ?? []);
    } catch (e) {
      if (!isMissingEndpoint(e)) setAddresses([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const remove = async (id?: number) => {
    if (id == null) return;
    setAddresses(prev => prev.filter(a => a.id !== id)); // optimistic
    try {
      await userApi.addressDelete(id);
    } catch {
      load(); // reconcile on failure
    }
  };

  return (
    <div className='container my-4 lm-user' style={{ maxWidth: 640 }}>
      <div className='d-flex justify-content-between align-items-center mb-3'>
        <h1 className='h4 mb-0'>My addresses</h1>
        <Button as={Link as any} to='/user/address/new' variant='primary' size='sm'>
          <i className='bi bi-plus-lg' /> Add address
        </Button>
      </div>

      {loading ? (
        <div className='text-center my-5'>
          <Spinner animation='border' />
        </div>
      ) : addresses.length === 0 ? (
        <p className='text-muted text-center my-5'>No saved addresses yet.</p>
      ) : (
        <div className='d-grid gap-2'>
          {addresses.map(a => (
            <div key={a.id} className='lm-addr-row'>
              <div>
                <div>
                  <strong>{a.name}</strong> <span className='text-muted'>{a.tel}</span>
                  {a.isDefault && <span className='badge bg-primary ms-2'>Default</span>}
                </div>
                <div className='text-muted small'>{[a.province, a.city, a.county, a.addressDetail].filter(Boolean).join(' ')}</div>
              </div>
              <div className='lm-addr-row__actions'>
                <Button variant='link' size='sm' onClick={() => navigate(`/user/address/${a.id}`)}>
                  Edit
                </Button>
                <Button variant='link' size='sm' className='text-danger' onClick={() => remove(a.id)}>
                  Delete
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default AddressList;
