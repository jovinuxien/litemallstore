import React, { useEffect, useState } from 'react';
import { Alert, Button, Card, Container, Form, Spinner } from 'react-bootstrap';

import { isMissingEndpoint, userApi } from 'app/shared/api';
import './user.scss';

/**
 * Profile view/edit, modelled on litemall-vue `user/user-information-set`.
 * Reads `/srv/user/index` for the current nickname/avatar/mobile and saves via
 * `/srv/user/profile`. Degrades to an editable-but-unsaved form if not live.
 */
const Profile: React.FC = () => {
  const [nickName, setNickName] = useState('');
  const [avatar, setAvatar] = useState('');
  const [mobile, setMobile] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [unavailable, setUnavailable] = useState(false);

  useEffect(() => {
    userApi
      .index()
      .then((d: any) => {
        setNickName(d?.nickName ?? '');
        setAvatar(d?.avatar ?? '');
        setMobile(d?.mobile ?? '');
      })
      .catch(e => {
        if (isMissingEndpoint(e)) setUnavailable(true);
      })
      .finally(() => setLoading(false));
  }, []);

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setSaved(false);
    try {
      await userApi.profileUpdate({ nickName, avatar, mobile });
      setSaved(true);
    } catch (err) {
      if (isMissingEndpoint(err)) setUnavailable(true);
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
    <Container className='my-4' style={{ maxWidth: 480 }}>
      <h1 className='h4 mb-3'>Profile</h1>
      <Card>
        <Card.Body>
          {unavailable && <Alert variant='warning'>Profile editing isn’t available yet.</Alert>}
          {saved && <Alert variant='success'>Profile updated.</Alert>}
          <Form onSubmit={save}>
            <Form.Group className='mb-3'>
              <Form.Label>Nickname</Form.Label>
              <Form.Control value={nickName} onChange={e => setNickName(e.target.value)} />
            </Form.Group>
            <Form.Group className='mb-3'>
              <Form.Label>Avatar URL</Form.Label>
              <Form.Control value={avatar} onChange={e => setAvatar(e.target.value)} />
            </Form.Group>
            <Form.Group className='mb-3'>
              <Form.Label>Mobile</Form.Label>
              <Form.Control value={mobile} onChange={e => setMobile(e.target.value)} />
            </Form.Group>
            <Button type='submit' variant='primary' disabled={saving}>
              {saving ? <Spinner animation='border' size='sm' /> : 'Save changes'}
            </Button>
          </Form>
        </Card.Body>
      </Card>
    </Container>
  );
};

export default Profile;
