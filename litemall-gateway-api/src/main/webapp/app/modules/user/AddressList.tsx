import React, { useCallback, useEffect, useState } from 'react';
import { Spinner } from 'react-bootstrap';
import { Link } from 'react-router-dom';

import { IAddress, userApi } from 'app/shared/api';
import { useTranslation } from 'app/i18n';
import { AddressCard, CellGroup, EmptyState, Page, PageHead } from 'app/components/commonComponents/storefront';
import './user.scss';

/**
 * Address book, modelled on litemall-vue `user/module-address`. Lists saved
 * addresses with edit/delete and an add button. Sourced from `/srv/address/*`;
 * graceful empty when not live (follow-up: order/user worktree).
 */
const AddressList: React.FC = () => {
  const { t } = useTranslation('user');
  const [addresses, setAddresses] = useState<IAddress[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const list = await userApi.addressList();
      setAddresses(list ?? []);
    } catch {
      setAddresses([]);
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
    <Page>
      <PageHead title={t('addresses.title')} />
      <div className='container'>
        <CellGroup>
          <div className='p-3 d-flex justify-content-end'>
            <Link to='/user/address/new' className='btn btn-lm-primary'>
              <i className='bi bi-plus-lg me-1' /> {t('addresses.addNew')}
            </Link>
          </div>
        </CellGroup>

        {loading ? (
          <div className='text-center my-5'>
            <Spinner animation='border' />
          </div>
        ) : addresses.length === 0 ? (
          <CellGroup>
            <EmptyState icon='bi-geo-alt' text={t('addresses.empty')}>
              <Link to='/user/address/new' className='btn btn-lm-primary'>
                {t('addresses.addNew')}
              </Link>
            </EmptyState>
          </CellGroup>
        ) : (
          <CellGroup>
            <div className='p-2 d-grid gap-2'>
              {addresses.map(a => (
                <AddressCard
                  key={a.id}
                  name={a.name}
                  tel={a.tel}
                  detail={[a.province, a.city, a.county, a.addressDetail].filter(Boolean).join(' ')}
                  isDefault={a.isDefault}
                  trailing={
                    <span className='d-inline-flex gap-2'>
                      <Link to={'/user/address/' + a.id} className='btn btn-sm btn-lm-outline'>
                        {t('addresses.edit')}
                      </Link>
                      <button type='button' className='btn btn-sm btn-lm-outline' onClick={() => remove(a.id)}>
                        {t('addresses.delete')}
                      </button>
                    </span>
                  }
                />
              ))}
            </div>
          </CellGroup>
        )}
      </div>
    </Page>
  );
};

export default AddressList;
