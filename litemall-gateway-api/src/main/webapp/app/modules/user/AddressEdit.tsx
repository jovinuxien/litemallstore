import React, { useEffect, useState } from 'react';
import { Alert, Form, Spinner } from 'react-bootstrap';
import { useNavigate, useParams } from 'react-router-dom';

import { IAddress, isMissingEndpoint, userApi } from 'app/shared/api';
import { CellGroup, Page, PageHead } from 'app/components/commonComponents/storefront';
import './user.scss';

const EMPTY: IAddress = { name: '', tel: '', province: '', city: '', county: '', addressDetail: '', postalCode: '', isDefault: false };

/**
 * Create/edit an address, modelled on litemall-vue `user/module-address-edit`.
 * `/user/address/new` creates; `/user/address/:id` edits. Saves via
 * `/srv/address/save`. Graceful when not live (follow-up: order/user worktree).
 */
const AddressEdit: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const isNew = !id || id === 'new';
  const [form, setForm] = useState<IAddress>(EMPTY);
  const [loading, setLoading] = useState(!isNew);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (isNew) return;
    userApi
      .addressDetail(Number(id))
      .then(a => setForm({ ...EMPTY, ...(a ?? {}) }))
      .catch(e => {
        if (!isMissingEndpoint(e)) setError('Could not load this address.');
      })
      .finally(() => setLoading(false));
  }, [id, isNew]);

  const set = (k: keyof IAddress) => (e: React.ChangeEvent<HTMLInputElement>) => setForm(prev => ({ ...prev, [k]: e.target.value }));

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await userApi.addressSave(form);
      navigate('/user/address');
    } catch (err) {
      if (isMissingEndpoint(err)) {
        setError('Saving addresses isn’t available yet.');
      } else {
        setError((err as { message?: string })?.message ?? 'Save failed.');
      }
    } finally {
      setSaving(false);
    }
  };

  const remove = async () => {
    if (form.id == null) return;
    setDeleting(true);
    setError(null);
    try {
      await userApi.addressDelete(form.id);
      navigate('/user/address');
    } catch (err) {
      if (isMissingEndpoint(err)) {
        setError('Deleting addresses isn’t available yet.');
      } else {
        setError((err as { message?: string })?.message ?? 'Delete failed.');
      }
    } finally {
      setDeleting(false);
    }
  };

  if (loading) {
    return (
      <div className='text-center my-5'>
        <Spinner animation='border' />
      </div>
    );
  }

  return (
    <Page>
      <PageHead title='Edit address' />
      <div className='container'>
        {error && <Alert variant='warning'>{error}</Alert>}
        <Form onSubmit={save}>
          <CellGroup>
            <div className='row g-3 p-3'>
              <div className='col-md-6'>
                <Form.Label>Recipient *</Form.Label>
                <Form.Control value={form.name ?? ''} onChange={set('name')} required />
              </div>
              <div className='col-md-6'>
                <Form.Label>Phone *</Form.Label>
                <Form.Control value={form.tel ?? ''} onChange={set('tel')} required />
              </div>
              <div className='col-md-4'>
                <Form.Label>Province / Region</Form.Label>
                <Form.Control value={form.province ?? ''} onChange={set('province')} />
              </div>
              <div className='col-md-4'>
                <Form.Label>City</Form.Label>
                <Form.Control value={form.city ?? ''} onChange={set('city')} />
              </div>
              <div className='col-md-4'>
                <Form.Label>District</Form.Label>
                <Form.Control value={form.county ?? ''} onChange={set('county')} />
              </div>
              <div className='col-12'>
                <Form.Label>Address detail *</Form.Label>
                <Form.Control value={form.addressDetail ?? ''} onChange={set('addressDetail')} required />
              </div>
              <div className='col-md-6'>
                <Form.Label>Postal code</Form.Label>
                <Form.Control value={form.postalCode ?? ''} onChange={set('postalCode')} />
              </div>
              <div className='col-md-6 d-flex align-items-end'>
                <Form.Check
                  type='checkbox'
                  label='Set as default'
                  checked={!!form.isDefault}
                  onChange={e => setForm(prev => ({ ...prev, isDefault: e.target.checked }))}
                />
              </div>
            </div>
          </CellGroup>

          <div className='mt-3 d-flex gap-2'>
            <button type='submit' className='btn btn-lm-primary' disabled={saving || deleting}>
              {saving ? <Spinner animation='border' size='sm' /> : 'Save'}
            </button>
            {!isNew && (
              <button type='button' className='btn btn-lm-outline' onClick={remove} disabled={saving || deleting}>
                {deleting ? <Spinner animation='border' size='sm' /> : 'Delete'}
              </button>
            )}
          </div>
        </Form>
      </div>
    </Page>
  );
};

export default AddressEdit;
