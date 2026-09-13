import React, { useEffect, useState } from 'react';
import { Alert, Form, Spinner } from 'react-bootstrap';
import { useNavigate, useParams } from 'react-router-dom';

import { contentApi, IAddress, IRegionNode, userApi } from 'app/shared/api';
import { useTranslation } from 'app/i18n';
import AddressAutocompleteInput from 'app/components/commonComponents/AddressAutocompleteInput';
import PhoneInput from 'app/components/commonComponents/PhoneInput';
import RegionInput from 'app/components/commonComponents/RegionInput';
import { CellGroup, Page, PageHead } from 'app/components/commonComponents/storefront';
import { SHIPPING_COUNTRIES } from 'app/shared/data/countries';
import { regionsFor } from 'app/shared/data/regions';
import './user.scss';

const EMPTY: IAddress = { name: '', tel: '', province: '', city: '', county: '', addressDetail: '', postalCode: '', countryCode: '', isDefault: false };

/**
 * Create/edit an address, modelled on litemall-vue `user/module-address-edit`.
 * `/user/address/new` creates; `/user/address/:id` edits. Saves via
 * `/srv/address/save`. Graceful when not live (follow-up: order/user worktree).
 */
const AddressEdit: React.FC = () => {
  const { t } = useTranslation('user');
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
  const [telValid, setTelValid] = useState(true);

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
      .catch(() => setError(t('addressEdit.loadFailed')))
      .finally(() => setLoading(false));
  }, [id, isNew]);

  const set = (k: keyof IAddress) => (e: React.ChangeEvent<HTMLInputElement>) => setForm(prev => ({ ...prev, [k]: e.target.value }));

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!telValid) {
      setError(t('addressEdit.phoneMismatch'));
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await userApi.addressSave(form);
      navigate('/user/address');
    } catch (err) {
      setError((err as { message?: string })?.message ?? t('addressEdit.saveFailed'));
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
      setError((err as { message?: string })?.message ?? t('addressEdit.deleteFailed'));
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
      <PageHead title={t('addressEdit.title')} />
      <div className='container'>
        {error && <Alert variant='warning'>{error}</Alert>}
        <Form onSubmit={save}>
          <CellGroup>
            <div className='row g-3 p-3'>
              <div className='col-md-6'>
                <Form.Label>{t('addressEdit.recipient')}</Form.Label>
                <Form.Control value={form.name ?? ''} onChange={set('name')} required />
              </div>
              <div className='col-md-6'>
                <Form.Label>{t('addressEdit.phone')}</Form.Label>
                {/* Country dial-code selector, stores one E.164 number. An
                    existing value is parsed back into country + digits, so
                    editing keeps the saved number visible (a legacy non-E.164
                    value keeps its digits and normalizes on retype). The form
                    renders only after the record loads (spinner gate above),
                    so mount-time seeding sees the saved value. */}
                <PhoneInput value={form.tel} onChange={tel => setForm(prev => ({ ...prev, tel }))} onValidityChange={setTelValid} />
              </div>
              <div className='col-md-6'>
                <Form.Label>{t('addressEdit.country')}</Form.Label>
                {/* Same list as checkout (V48 country_code); powers the region
                    type-ahead below and pre-fills checkout's destination country. */}
                <Form.Select
                  value={form.countryCode ?? ''}
                  onChange={e => setForm(prev => ({ ...prev, countryCode: e.target.value }))}
                >
                  <option value=''>{t('addressEdit.selectCountry')}</option>
                  {SHIPPING_COUNTRIES.map(c => (
                    <option key={c.code} value={c.code}>
                      {c.name}
                    </option>
                  ))}
                </Form.Select>
              </div>
              {regions.length > 0 && (
                <div className='col-12'>
                  <Form.Check
                    type='switch'
                    id='region-cascade-switch'
                    label={t('addressEdit.cascade')}
                    checked={useCascade}
                    onChange={e => setUseCascade(e.target.checked)}
                  />
                </div>
              )}
              {useCascade && regions.length > 0 ? (
                <>
                  <div className='col-md-4'>
                    <Form.Label>{t('addressEdit.province')}</Form.Label>
                    <Form.Select
                      value={form.province ?? ''}
                      onChange={e => setForm(prev => ({ ...prev, province: e.target.value, city: '', county: '' }))}
                    >
                      <option value=''>{t('addressEdit.select')}</option>
                      {regions.map(r => (
                        <option key={r.id} value={r.name}>
                          {r.name}
                        </option>
                      ))}
                    </Form.Select>
                  </div>
                  <div className='col-md-4'>
                    <Form.Label>{t('addressEdit.city')}</Form.Label>
                    <Form.Select
                      value={form.city ?? ''}
                      disabled={!province}
                      onChange={e => setForm(prev => ({ ...prev, city: e.target.value, county: '' }))}
                    >
                      <option value=''>{t('addressEdit.select')}</option>
                      {(province?.children ?? []).map(c => (
                        <option key={c.id} value={c.name}>
                          {c.name}
                        </option>
                      ))}
                    </Form.Select>
                  </div>
                  <div className='col-md-4'>
                    <Form.Label>{t('addressEdit.district')}</Form.Label>
                    <Form.Select
                      value={form.county ?? ''}
                      disabled={!city}
                      onChange={e => setForm(prev => ({ ...prev, county: e.target.value }))}
                    >
                      <option value=''>{t('addressEdit.select')}</option>
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
                    <Form.Label>{t('addressEdit.province')}</Form.Label>
                    {/* Type-ahead over the selected country's regions; free text
                        stays valid (countries without data = plain input). */}
                    <RegionInput
                      value={form.province ?? ''}
                      onChange={text => setForm(prev => ({ ...prev, province: text }))}
                      suggestions={regionsFor(form.countryCode)}
                    />
                  </div>
                  <div className='col-md-4'>
                    <Form.Label>{t('addressEdit.city')}</Form.Label>
                    <Form.Control value={form.city ?? ''} onChange={set('city')} />
                  </div>
                  <div className='col-md-4'>
                    <Form.Label>{t('addressEdit.district')}</Form.Label>
                    <Form.Control value={form.county ?? ''} onChange={set('county')} />
                  </div>
                </>
              )}
              <div className='col-12'>
                <Form.Label>{t('addressEdit.detail')}</Form.Label>
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
                      countryCode: SHIPPING_COUNTRIES.some(c => c.code === parts.countryCode)
                        ? parts.countryCode
                        : prev.countryCode,
                    }))
                  }
                  required
                />
              </div>
              <div className='col-md-6'>
                <Form.Label>{t('addressEdit.postalCode')}</Form.Label>
                <Form.Control value={form.postalCode ?? ''} onChange={set('postalCode')} />
              </div>
              <div className='col-md-6 d-flex align-items-end'>
                <Form.Check
                  type='checkbox'
                  label={t('addressEdit.setDefault')}
                  checked={!!form.isDefault}
                  onChange={e => setForm(prev => ({ ...prev, isDefault: e.target.checked }))}
                />
              </div>
            </div>
          </CellGroup>

          <div className='mt-3 d-flex gap-2'>
            <button type='submit' className='btn btn-lm-primary' disabled={saving || deleting}>
              {saving ? <Spinner animation='border' size='sm' /> : t('addressEdit.save')}
            </button>
            {!isNew && (
              <button type='button' className='btn btn-lm-outline' onClick={remove} disabled={saving || deleting}>
                {deleting ? <Spinner animation='border' size='sm' /> : t('addressEdit.delete')}
              </button>
            )}
          </div>
        </Form>
      </div>
    </Page>
  );
};

export default AddressEdit;
