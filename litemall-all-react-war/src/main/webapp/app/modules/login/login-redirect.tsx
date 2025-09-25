import { REDIRECT_URL } from 'app/shared/util/url-utils';
import { useEffect } from 'react';
import { useLocation } from 'react-router';

export const LoginRedirect = () => {
  const pageLocation = useLocation();

  useEffect(() => {
    localStorage.setItem(REDIRECT_URL, pageLocation.state.from.pathname);
    window.location.reload();
  });

  return null;
};

export default LoginRedirect;
