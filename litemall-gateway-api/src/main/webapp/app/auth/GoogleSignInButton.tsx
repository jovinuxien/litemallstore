import React, { useEffect, useRef, useState } from 'react';

import { useAppDispatch } from 'app/config/store';
import { loadSiteConfig } from 'app/shared/config/siteConfig';
import { googleSignInThunk } from 'app/auth/customerAuthSlice';
import { fireRegisterGifts } from 'app/shared/util/couponFormat';

/**
 * "Sign in with Google" (Wave 16) — env-gated via /auth/site-config
 * `googleClientId`: null ⇒ this component renders NOTHING and loads nothing
 * (byte-identical behavior to the pre-Wave-16 page). When configured, the GIS
 * script loads on first mount, Google renders its own button, and the returned
 * credential is verified SERVER-SIDE at /auth/google — the SPA never trusts
 * the token itself. Functional auth, not tracking: outside the cookie-consent
 * gate by design, and loaded only on the auth pages that show the button.
 */

declare global {
  interface Window {
    google?: {
      accounts?: {
        id?: {
          initialize: (config: { client_id: string; callback: (r: { credential?: string }) => void }) => void;
          renderButton: (el: HTMLElement, options: Record<string, unknown>) => void;
        };
      };
    };
  }
}

let gisLoading: Promise<void> | null = null;
const loadGis = (): Promise<void> => {
  if (window.google?.accounts?.id) return Promise.resolve();
  if (!gisLoading) {
    gisLoading = new Promise<void>((resolve, reject) => {
      const script = document.createElement('script');
      script.src = 'https://accounts.google.com/gsi/client';
      script.async = true;
      script.onload = () => resolve();
      script.onerror = () => {
        gisLoading = null;
        reject(new Error('gis load failed'));
      };
      document.head.appendChild(script);
    });
  }
  return gisLoading;
};

const GoogleSignInButton: React.FC<{ onSuccess?: () => void }> = ({ onSuccess }) => {
  const dispatch = useAppDispatch();
  const holder = useRef<HTMLDivElement>(null);
  const [error, setError] = useState<string | null>(null);
  const [configured, setConfigured] = useState(false);

  useEffect(() => {
    let cancelled = false;
    loadSiteConfig().then(cfg => {
      if (cancelled || !cfg.googleClientId) return;
      setConfigured(true);
      loadGis()
        .then(() => {
          if (cancelled || !holder.current || !window.google?.accounts?.id) return;
          window.google.accounts.id.initialize({
            client_id: cfg.googleClientId!,
            callback: async r => {
              if (!r.credential) return;
              const result = await dispatch(googleSignInThunk({ credential: r.credential }));
              if (googleSignInThunk.fulfilled.match(result)) {
                setError(null);
                // Wave 18: the SPA cannot tell a first-time Google provisioning
                // from a repeat sign-in, so fire the register-gift grant on
                // every success — idempotent server-side (per-user claim
                // limit), fail-silent, never blocks the flow.
                fireRegisterGifts();
                onSuccess?.();
              } else {
                setError((result.payload as { errmsg?: string } | undefined)?.errmsg ?? 'Google sign-in failed');
              }
            },
          });
          window.google.accounts.id.renderButton(holder.current, { theme: 'outline', size: 'large', width: 320 });
        })
        .catch(() => {
          // Script blocked/offline: quietly show nothing — password auth stands.
        });
    });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (!configured) return null;
  return (
    <div className='my-2'>
      <div ref={holder} className='d-flex justify-content-center' />
      {error && (
        <div className='small text-danger text-center mt-1' role='status'>
          {error}
        </div>
      )}
    </div>
  );
};

export default GoogleSignInButton;
