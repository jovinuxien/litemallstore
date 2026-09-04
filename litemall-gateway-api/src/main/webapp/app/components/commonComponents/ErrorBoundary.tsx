import React from 'react';
import { Link, useLocation } from 'react-router-dom';

import { t } from 'app/i18n';

/**
 * The storefront had no error boundary at all: `index.tsx` rendered
 * `<Provider><App/></Provider>` and nothing caught a render exception, so ONE
 * throwing component blanked the whole shop — no header, no way back, and no
 * signal to us. The admin panel learned this the expensive way when a DIY page
 * threw rendering an array-shaped LocalDateTime; the storefront, which is the
 * surface with customers on it, never got the same treatment.
 *
 * Two placements, doing different jobs:
 *  - {@link RouteErrorBoundary} wraps Layout's `<Outlet/>`, so a crashed PAGE
 *    keeps the header, the search box and the category nav. The shopper can
 *    carry on shopping instead of meeting a white screen.
 *  - {@link RootErrorBoundary} wraps the app outside the router, for the case
 *    where the shell itself throws. It can only offer a reload, because there
 *    is no router above it to navigate with — that is the last resort, and it
 *    is still enormously better than nothing.
 *
 * Resetting on navigation is the part the old orphaned stub in
 * `views/commonViews/boundaryerror` lacked, and without it a boundary is a trap:
 * React keeps the fallback mounted until state changes, so the first crash makes
 * every SUBSEQUENT route look broken too until a hard reload.
 */

interface Props {
  children: React.ReactNode;
  /** Changing this clears a caught error — the route key at the call site. */
  resetKey?: string;
  fallback: (retry: () => void) => React.ReactNode;
}

interface State {
  error: Error | null;
}

export class ErrorBoundary extends React.Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { error: null };
    this.retry = this.retry.bind(this);
  }

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo): void {
    // No error-reporting service is wired up, so this is the only trace there
    // is. Do not remove it in favour of silence, and do not tell the shopper we
    // have "reported" anything — we have not.
    // eslint-disable-next-line no-console
    console.error('Storefront render error:', error, info.componentStack);
  }

  componentDidUpdate(prev: Props): void {
    if (this.state.error && prev.resetKey !== this.props.resetKey) {
      this.setState({ error: null });
    }
  }

  retry(): void {
    this.setState({ error: null });
  }

  render(): React.ReactNode {
    if (this.state.error) return this.props.fallback(this.retry);
    return this.props.children;
  }
}

/** Page-level: the shell survives, so there is always a way onward. */
export const RouteErrorBoundary: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const location = useLocation();
  return (
    <ErrorBoundary
      resetKey={`${location.pathname}${location.search}`}
      fallback={retry => (
        <div className='container my-5 text-center'>
          {/* Plain t(): a boundary fallback must not depend on hooks or context that
              may be what just threw. English is bundled, so this always renders. */}
          <h1 className='h4 mb-3'>{t('errorPage.title')}</h1>
          <p className='text-muted mb-4'>{t('errorPage.body')}</p>
          <div className='d-flex gap-2 justify-content-center flex-wrap'>
            <button type='button' className='btn btn-primary' onClick={retry}>
              {t('errorPage.tryAgain')}
            </button>
            <Link to='/' className='btn btn-outline-secondary'>
              {t('errorPage.home')}
            </Link>
            <Link to='/search' className='btn btn-outline-secondary'>
              {t('errorPage.browse')}
            </Link>
          </div>
        </div>
      )}
    >
      {children}
    </ErrorBoundary>
  );
};

/** Last resort, outside the router: no navigation available, only a reload. */
export const RootErrorBoundary: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <ErrorBoundary
    fallback={() => (
      <div style={{ maxWidth: 520, margin: '4rem auto', padding: '0 1rem', textAlign: 'center', fontFamily: 'system-ui, sans-serif' }}>
        <h1 style={{ fontSize: '1.25rem', marginBottom: '0.75rem' }}>{t('rootError.title')}</h1>
        <p style={{ color: '#555', marginBottom: '1.5rem' }}>{t('rootError.body')}</p>
        <button
          type='button'
          onClick={() => window.location.reload()}
          style={{ padding: '0.5rem 1.25rem', border: '1px solid #0a5d65', background: '#0a5d65', color: '#fff', borderRadius: 4, cursor: 'pointer' }}
        >
          {t('rootError.reload')}
        </button>
      </div>
    )}
  >
    {children}
  </ErrorBoundary>
);

export default ErrorBoundary;
