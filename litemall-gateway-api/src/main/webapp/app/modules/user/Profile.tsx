import React, { useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Container, Form, Spinner } from 'react-bootstrap';
import { Link } from 'react-router-dom';

import { authApi, userApi } from 'app/shared/api';
import './user.scss';

/**
 * Profile view/edit against the auth edge (Wave 4 Task A): reads
 * `GET /auth/me`, saves via `POST /auth/profile` (partial update). The avatar
 * is uploaded as a file through `POST /srv/storage/upload` (goods-management
 * Wave 4 — LIVE, verified 2026-07-13; the isMissingEndpoint URL-field
 * fallback was removed).
 */
const Profile: React.FC = () => {
  const [nickname, setNickname] = useState('');
  const [email, setEmail] = useState('');
  const [avatar, setAvatar] = useState('');
  const [mobile, setMobile] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [mobileTaken, setMobileTaken] = useState(false);
  const [uploading, setUploading] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    authApi
      .me()
      .then(env => {
        if (env.errno === 0 && env.data) {
          setNickname(env.data.nickName ?? '');
          setEmail(env.data.email ?? '');
          setAvatar(env.data.avatarUrl ?? '');
          setMobile(env.data.mobile ?? '');
        }
      })
      .catch(() => setError('Could not load your profile.'))
      .finally(() => setLoading(false));
  }, []);

  const pickAvatar = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setUploading(true);
    setError(null);
    try {
      const stored = await userApi.storageUpload(file);
      const url = stored?.url;
      if (url) {
        setAvatar(url);
      } else {
        setError('Upload succeeded but returned no URL.');
      }
    } catch {
      setError('Avatar upload failed.');
    } finally {
      setUploading(false);
      if (fileRef.current) fileRef.current.value = '';
    }
  };

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setSaved(false);
    setError(null);
    setMobileTaken(false);
    try {
      const env = await authApi.profile({ nickname, email, mobile, avatar });
      if (env.errno === 0) {
        setSaved(true);
        if (env.data) {
          setNickname(env.data.nickName ?? '');
          setEmail(env.data.email ?? '');
          setAvatar(env.data.avatarUrl ?? '');
          setMobile(env.data.mobile ?? '');
        }
      } else if (env.errno === 705) {
        setMobileTaken(true);
      } else {
        setError(env.errmsg || 'Profile update failed.');
      }
    } catch {
      setError('Profile update failed.');
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
          {error && <Alert variant='danger'>{error}</Alert>}
          {saved && <Alert variant='success'>Profile updated.</Alert>}
          <Form onSubmit={save}>
            <Form.Group className='mb-3'>
              <Form.Label>Nickname</Form.Label>
              <Form.Control value={nickname} onChange={e => setNickname(e.target.value)} />
            </Form.Group>
            <Form.Group className='mb-3'>
              <Form.Label>Email</Form.Label>
              <Form.Control type='email' value={email} onChange={e => setEmail(e.target.value)} />
            </Form.Group>
            <Form.Group className='mb-3'>
              <Form.Label>Avatar</Form.Label>
              {avatar && (
                <div className='mb-2'>
                  <img src={avatar} alt='avatar' style={{ width: 64, height: 64, objectFit: 'cover', borderRadius: '50%' }} />
                </div>
              )}
              <Form.Control ref={fileRef} type='file' accept='image/*' onChange={pickAvatar} disabled={uploading} />
              {uploading && (
                <Form.Text className='text-muted'>
                  <Spinner animation='border' size='sm' /> Uploading…
                </Form.Text>
              )}
            </Form.Group>
            <Form.Group className='mb-3'>
              <Form.Label>Mobile</Form.Label>
              <Form.Control value={mobile} onChange={e => setMobile(e.target.value)} isInvalid={mobileTaken} />
              <Form.Control.Feedback type='invalid'>This mobile number is already registered.</Form.Control.Feedback>
            </Form.Group>
            <Button type='submit' variant='primary' disabled={saving || uploading}>
              {saving ? <Spinner animation='border' size='sm' /> : 'Save changes'}
            </Button>
            <Link to='/reset' className='btn btn-outline-secondary ms-2'>
              Change password
            </Link>
          </Form>
        </Card.Body>
      </Card>
    </Container>
  );
};

export default Profile;
