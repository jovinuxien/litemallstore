import React, { useEffect, useState } from 'react';
import { Alert, Form, Spinner } from 'react-bootstrap';
import { useNavigate, useParams } from 'react-router-dom';

import { contentApi, IAddress, IRegionNode, userApi } from 'app/shared/api';
import AddressAutocompleteInput from 'app/components/commonComponents/AddressAutocompleteInput';
import PhoneInput from 'app/components/commonComponents/PhoneInput';
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
  // Region cascade (goods-management Wave 4, /srv/region/clist — China-only
  // data). Tree present ⇒ offer province/city/county dropdowns; call failing
  // or empty (backend not merged yet, or non-CN deployment) ⇒ free-text only.
  const [regions, setRegions] = useState<IRegionNode[]>([]);
  const [useCascade, setUseCascade] = useState(false);

  useEffect(() => {
    contentApi
      .regionCList()
      .then(tree => setRegions(Array.isArray(tree) ? tree : []))
      .catch(() => setRegions([]));
  }, []);

  const province = regions.find(r => r.name === form.province);
  const city = province?.children?.find(c => c.name === form.city);

  useEffect(() => {
    if (isNew) return;
    userApi
      .addressDetail(Number(id))
      .then(a => setForm({ ...EMPTY, ...(a ?? {}) }))
      .catch(() => setError('Could not load this address.'))
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
      setError((err as { message?: string })?.message ?? 'Save failed.');
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
      setError((err as { message?: string })?.message ?? 'Delete failed.');
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
                {/* Country dial-code selector, stores one E.164 number. An
                    existing value is parsed back into country + digits, so
                    editing keeps the saved number visible (a legacy non-E.164
                    value keeps its digits and normalizes on retype). The form
                    renders only after the record loads (spinner gate above),
                    so mount-time seeding sees the saved value. */}
                <PhoneInput value={form.tel} onChange={tel => setForm(prev => ({ ...prev, tel }))} />
              </div>
              {regions.length > 0 && (
                <div className='col-12'>
                  <Form.Check
                    type='switch'
                    id='region-cascade-switch'
                    label='Pick region from list (China addresses)'
                    checked={useCascade}
                    onChange={e => setUseCascade(e.target.checked)}
                  />
                </div>
              )}
              {useCascade && regions.length > 0 ? (
                <>
                  <div className='col-md-4'>
                    <Form.Label>Province / Region</Form.Label>
                    <Form.Select
                      value={form.province ?? ''}
                      onChange={e => setForm(prev => ({ ...prev, province: e.target.value, city: '', county: '' }))}
                    >
                      <option value=''>Select…</option>
                      {regions.map(r => (
                        <option key={r.id} value={r.name}>
                          {r.name}
                        </option>
                      ))}
                    </Form.Select>
                  </div>
                  <div className='col-md-4'>
                    <Form.Label>City</Form.Label>
                    <Form.Select
                      value={form.city ?? ''}
                      disabled={!province}
                      onChange={e => setForm(prev => ({ ...prev, city: e.target.value, county: '' }))}
                    >
                      <option value=''>Select…</option>
                      {(province?.children ?? []).map(c => (
                        <option key={c.id} value={c.name}>
                          {c.name}
                        </option>
                      ))}
                    </Form.Select>
                  </div>
                  <div className='col-md-4'>
                    <Form.Label>District</Form.Label>
                    <Form.Select
                      value={form.county ?? ''}
                      disabled={!city}
                      onChange={e => setForm(prev => ({ ...prev, county: e.target.value }))}
                    >
                      <option value=''>Select…</option>
                      {(city?.children ?? []).map(d => (
                        <option key={d.id} value={d.name}>
                          {d.name}
                        </option>
                      ))}
                    </Form.Select>
                  </div>
                </>
              ) : (
                <>
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
                </>
              )}
              <div className='col-12'>
                <Form.Label>Address detail *</Form.Label>
                {/* Wave 16: env-gated Places suggestions; unset key ⇒ plain input. */}
                <AddressAutocompleteInput
                  value={form.addressDetail ?? ''}
                  onChange={text => setForm(prev => ({ ...prev, addressDetail: text }))}
                  onResolved={parts =>
                    setForm(prev => ({
                      ...prev,
                      addressDetail: parts.line,
                      city: parts.city ?? prev.city,
                      province: parts.region ?? prev.province,
                      postalCode: parts.postalCode ?? prev.postalCode,
                    }))
                  }
                  required
                />
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
