import React from 'react';

import './auth.scss';

/**
 * Shared themed frame for the auth pages (Wave 16) — teal accent bar, card,
 * title/subtitle. Pages keep their own forms; this is presentation only.
 */
const AuthShell: React.FC<{ title: string; sub?: string; children: React.ReactNode }> = ({ title, sub, children }) => (
  <div className='lm-auth'>
    <div className='lm-auth__card'>
      <div className='lm-auth__accent' />
      <div className='lm-auth__body'>
        <h1 className='h4 lm-auth__title'>{title}</h1>
        {sub && <div className='lm-auth__sub'>{sub}</div>}
        {children}
      </div>
    </div>
  </div>
);

export default AuthShell;
