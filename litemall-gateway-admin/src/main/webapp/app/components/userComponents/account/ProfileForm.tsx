import { useAppDispatch, useAppSelector } from 'app/config/store';
import React from 'react';
import { Controller, useForm } from 'react-hook-form';
import { setImagePreview } from './FormProfileSlice';

type ProfileForm = {
  formFile: FileList;
  name: string;
  mobileNo: string;
  email: string;
  location: string;
  dob: string;
};

const ProfileForm: React.FC = props => {
  const dispatch = useAppDispatch();
  const imagePreview = useAppSelector(state => state.profileForm.imagePreview);

  const {
    control,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ProfileForm>();

  const onSubmit = (data: ProfileForm) => {
    console.log(data);
    alert('Form submitted successfully!');
  };
  function handleImageChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (file) {
      const reader = new FileReader();
      reader.onload = () => {
        dispatch(setImagePreview(reader.result as string));
      };
      reader.readAsDataURL(file);
    } else {
      dispatch(setImagePreview(null));
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} className='needs-validation' noValidate>
      <div className='card border-primary'>
        <h6 className='card-header'>
          <i className='bi bi-person-lines-fill' /> Profile Detail
        </h6>
        <img src={imagePreview || '../../images/NO_IMG.png'} alt='Profile Preview' className='card-img-top rounded-0 img-fluid bg-secondary' />
        <Controller
          name='formFile'
          control={control}
          render={({ field }) => (
            <input
              type='file'
              accept='image/*'
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
                field.onChange(e.target.files);
                handleImageChange(e);
              }}
              ref={field.ref}
            />
          )}
        />
        <ul className='list-group list-group-flush'>
          {/** Name Field */}
          <li className='list-group-item'>
            <Controller
              name='name'
              control={control}
              rules={{ required: 'Name is required' }}
              render={({ field }) => <input type='text' placeholder='Your name' className={`form-control ${errors.name ? 'is-invalid' : ''}`} {...field} />}
            />
            {errors.name && <p className='text-danger'>{errors.name.message}</p>}
          </li>
          {/** Mobile Number Field */}
          <li className='list-group-item'>
            <Controller
              name='mobileNo'
              control={control}
              rules={{
                required: 'Mobile number is required',
                minLength: { value: 4, message: 'Minimum 4 digits required' },
                maxLength: { value: 15, message: 'Maximum 15 digits allowed' },
                pattern: { value: /^\d+$/, message: 'Only digits are allowed' },
              }}
              render={({ field }) => (
                <input
                  type='number'
                  placeholder='Mobile no without country code'
                  className={`form-control ${errors.mobileNo ? 'is-invalid' : ''}`}
                  {...field}
                />
              )}
            />
            {errors.mobileNo && <p className='text-danger'>{errors.mobileNo.message}</p>}
          </li>
          {/** Email Field */}
          <li className='list-group-item'>
            <Controller
              name='email'
              control={control}
              rules={{
                required: 'Email is required',
                pattern: {
                  value: /^[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\.[a-zA-Z0-9-.]+$/,
                  message: 'Invalid email address',
                },
              }}
              render={({ field }) => <input type='email' placeholder='Your email' className={`form-control ${errors.email ? 'is-invalid' : ''}`} {...field} />}
            />
            {errors.email && <p className='text-danger'>{errors.email.message}</p>}
          </li>
          {/** Location Field */}
          <li className='list-group-item'>
            <Controller
              name='location'
              control={control}
              rules={{ required: 'Location is required' }}
              render={({ field }) => (
                <input type='text' placeholder='Your location' className={`form-control ${errors.location ? 'is-invalid' : ''}`} {...field} />
              )}
            />
            {errors.location && <p className='text-danger'>{errors.location.message}</p>}
          </li>
          {/** Date of Birth Field */}
          <li className='list-group-item'>
            <Controller
              name='dob'
              control={control}
              rules={{ required: 'Date of birth is required' }}
              render={({ field }) => <input type='date' placeholder='Your birthdate' className={`form-control ${errors.dob ? 'is-invalid' : ''}`} {...field} />}
            />
            {errors.dob && <p className='text-danger'>{errors.dob.message}</p>}
          </li>
        </ul>
        <div className='card-body'>
          <button type='submit' className='btn btn-primary d-flex' disabled={isSubmitting}>
            Submit
          </button>
        </div>
      </div>
    </form>
  );
};

export default ProfileForm;
