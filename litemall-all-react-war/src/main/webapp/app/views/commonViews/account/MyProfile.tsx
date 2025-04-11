import { useAppDispatch, useAppSelector } from 'app/config/hooks';
import { getUserInfo } from 'app/shared/reducers/profileSlice';
import React, { lazy, useEffect, useState } from 'react';
import { Card, Col, Container, Row, Tab, Tabs } from 'react-bootstrap';
import { SubmitHandler, useForm } from 'react-hook-form';
import { useParams } from 'react-router-dom';
const ProfileForm = lazy(() => import('../../../components/userComponents/account/ProfileForm'));
//const ChangePasswordForm = lazy(() => import('../../components/account/ChangePasswordForm'));
const SettingForm = lazy(() => import('../../../components/userComponents/account/SettingForm'));
const CardListForm = lazy(() => import('../../../components/userComponents/account/CardListForm'));

interface ProfileFormValues {
  name: string;
  email: string;
}

interface ChangePasswordFormValues {
  currentPassword: string;
  newPassword: string;
}

const MyProfileView: React.FC = () => {
  const { userId } = useParams<{ userId: string }>();

  const [imagePreview, setImagePreview] = useState<string>('');
  const dispatch = useAppDispatch();
  //const isDeleting = useAppSelector(state => state.profile.isDeleting);
  const { isAuthenticated } = useAppSelector(state => state.auth.data);
  const profileState = useAppSelector(state => state.profile.data);

  const { register, handleSubmit } = useForm<ProfileFormValues>();
  const { register: registerPassword, handleSubmit: handleSubmitPassword } = useForm<ChangePasswordFormValues>();

  const onSubmitProfile: SubmitHandler<ProfileFormValues> = async values => {
    alert(JSON.stringify(values));
    /* dispatch(updateProfile()); */
  };

  const onSubmitChangePassword: SubmitHandler<ChangePasswordFormValues> = async values => {
    alert(JSON.stringify(values));
    /* dispatch(updatePassword()); */
  };

  const onImageChange = async (file: File | null) => {
    if (file) {
      const val = await getBase64(file);
      setImagePreview(val as string);
    } else {
      setImagePreview('');
    }
  };

  const getBase64 = (file: File): Promise<string | ArrayBuffer | null> => {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result);
      reader.onerror = error => reject(error);
      reader.readAsDataURL(file);
    });
  };

  /* const fetchUserInfo = async () => {
    try {
      const data = await dispatch(getInfoUser());
      console.log(data);
    } catch (error) {
      console.error('Fetch User Info Error:', error);
    }
  }; */

  useEffect(() => {
    dispatch(getUserInfo());
  }, [dispatch]);

  return (
    <Container className='py-5'>
      <Row className='justify-content-center'>
        <Col md={10} lg={8}>
          <Card>
            <Card.Body>
              {/*  {isAuthenticated ? ( */}
              <>
                <h2 className='mb-4'>Welcome, {profileState.nickName}</h2>
                <Tabs defaultActiveKey='profile' id='profile-tabs' className='mb-3'>
                  <Tab eventKey='profile' title='Profile'>
                    {/* <ProfileForm user={profileState} onSubmit={onSubmitProfile} /> */}
                  </Tab>
                  <Tab eventKey='password' title='Change Password'>
                    <form onSubmit={handleSubmitPassword(onSubmitChangePassword)}>
                      <div className='mb-3'>
                        <label htmlFor='currentPassword' className='form-label'>
                          Current Password
                        </label>
                        <input id='currentPassword' type='password' className='form-control' {...registerPassword('currentPassword')} />
                      </div>
                      <div className='mb-3'>
                        <label htmlFor='newPassword' className='form-label'>
                          New Password
                        </label>
                        <input id='newPassword' type='password' className='form-control' {...registerPassword('newPassword')} />
                      </div>
                      <button type='submit' className='btn btn-primary'>
                        Change Password
                      </button>
                    </form>
                  </Tab>
                  <Tab eventKey='settings' title='Settings'>
                    <SettingForm />
                  </Tab>
                  <Tab eventKey='cards' title='Payment Methods'>
                    <CardListForm />
                  </Tab>
                </Tabs>
              </>
              {/* ) : ( */}
              {/* <div className='text-center'>
                <h2 className='mb-4'>Please Log In</h2>
                <p>You need to be logged in to view and edit your profile.</p>
                <Link to='/account/signin' className='btn btn-primary'>
                  Sign in
                </Link>
              </div> */}
              {/*  )} */}
            </Card.Body>
          </Card>
        </Col>
      </Row>
    </Container>
  );
};

export default MyProfileView;
