import React, { useEffect, useState } from 'react';
import { Alert, Button, Card, Col, Container, Form, Row, Spinner } from 'react-bootstrap';
import { useNavigate, useParams } from 'react-router-dom';

import { IAddress, isMissingEndpoint, userApi } from 'app/shared/api';
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

  if (loading) {
    return (
      <div className='text-center my-5'>
        <Spinner animation='border' />
      </div>
    );
  }

  return (
    <Container className='my-4' style={{ maxWidth: 560 }}>
      <h1 className='h4 mb-3'>{isNew ? 'Add address' : 'Edit address'}</h1>
      <Card>
        <Card.Body>
          {error && <Alert variant='warning'>{error}</Alert>}
          <Form onSubmit={save}>
            <Row className='g-3'>
              <Col md={6}>
                <Form.Label>Recipient *</Form.Label>
                <Form.Control value={form.name ?? ''} onChange={set('name')} required />
              </Col>
              <Col md={6}>
                <Form.Label>Phone *</Form.Label>
                <Form.Control value={form.tel ?? ''} onChange={set('tel')} required />
              </Col>
              <Col md={4}>
                <Form.Label>Province / Region</Form.Label>
                <Form.Control value={form.province ?? ''} onChange={set('province')} />
              </Col>
              <Col md={4}>
                <Form.Label>City</Form.Label>
                <Form.Control value={form.city ?? ''} onChange={set('city')} />
              </Col>
              <Col md={4}>
                <Form.Label>District</Form.Label>
                <Form.Control value={form.county ?? ''} onChange={set('county')} />
              </Col>
              <Col md={12}>
                <Form.Label>Address detail *</Form.Label>
                <Form.Control value={form.addressDetail ?? ''} onChange={set('addressDetail')} required />
              </Col>
              <Col md={6}>
                <Form.Label>Postal code</Form.Label>
                <Form.Control value={form.postalCode ?? ''} onChange={set('postalCode')} />
              </Col>
              <Col md={6} className='d-flex align-items-end'>
                <Form.Check
                  type='checkbox'
                  label='Set as default'
                  checked={!!form.isDefault}
                  onChange={e => setForm(prev => ({ ...prev, isDefault: e.target.checked }))}
                />
              </Col>
            </Row>
            <div className='mt-3 d-flex gap-2'>
              <Button type='submit' variant='primary' disabled={saving}>
                {saving ? <Spinner animation='border' size='sm' /> : 'Save'}
              </Button>
              <Button variant='outline-secondary' onClick={() => navigate('/user/address')}>
                Cancel
              </Button>
            </div>
          </Form>
        </Card.Body>
      </Card>
    </Container>
  );
};

export default AddressEdit;
