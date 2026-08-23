import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes, Link } from 'react-router-dom';

import { ErrorBoundary, RouteErrorBoundary } from './ErrorBoundary';

/**
 * The storefront shipped with NO error boundary, so any render exception blanked
 * the whole shop. These tests pin the two properties that make a boundary worth
 * having: it contains the damage, and it lets go afterwards.
 *
 * React logs caught errors to console.error by design; silenced so a passing
 * suite stays readable.
 */
const Boom: React.FC<{ throws?: boolean }> = ({ throws = true }) => {
  if (throws) throw new Error('kaboom');
  return <div>page content</div>;
};

let consoleError: jest.SpyInstance;
beforeEach(() => {
  consoleError = jest.spyOn(console, 'error').mockImplementation(() => undefined);
});
afterEach(() => consoleError.mockRestore());

describe('ErrorBoundary', () => {
  it('renders the fallback instead of propagating the crash', () => {
    render(
      <ErrorBoundary fallback={() => <div>fallback shown</div>}>
        <Boom />
      </ErrorBoundary>
    );
    expect(screen.getByText('fallback shown')).toBeTruthy();
  });

  it('renders children untouched when nothing throws', () => {
    render(
      <ErrorBoundary fallback={() => <div>fallback shown</div>}>
        <Boom throws={false} />
      </ErrorBoundary>
    );
    expect(screen.getByText('page content')).toBeTruthy();
    expect(screen.queryByText('fallback shown')).toBeNull();
  });

  it('clears the error when the resetKey changes — otherwise the first crash poisons every later page', () => {
    const { rerender } = render(
      <ErrorBoundary resetKey='/a' fallback={() => <div>fallback shown</div>}>
        <Boom />
      </ErrorBoundary>
    );
    expect(screen.getByText('fallback shown')).toBeTruthy();

    rerender(
      <ErrorBoundary resetKey='/b' fallback={() => <div>fallback shown</div>}>
        <Boom throws={false} />
      </ErrorBoundary>
    );
    expect(screen.getByText('page content')).toBeTruthy();
  });

  it('does not clear while the resetKey is unchanged', () => {
    const { rerender } = render(
      <ErrorBoundary resetKey='/a' fallback={() => <div>fallback shown</div>}>
        <Boom />
      </ErrorBoundary>
    );
    rerender(
      <ErrorBoundary resetKey='/a' fallback={() => <div>fallback shown</div>}>
        <Boom throws={false} />
      </ErrorBoundary>
    );
    expect(screen.getByText('fallback shown')).toBeTruthy();
  });

  it('hands the fallback a retry that clears the error in place', () => {
    let shouldThrow = true;
    const Flaky: React.FC = () => {
      if (shouldThrow) throw new Error('kaboom');
      return <div>page content</div>;
    };
    render(
      <ErrorBoundary
        fallback={retry => (
          <button type='button' onClick={retry}>
            Try again
          </button>
        )}
      >
        <Flaky />
      </ErrorBoundary>
    );
    shouldThrow = false;
    fireEvent.click(screen.getByText('Try again'));
    expect(screen.getByText('page content')).toBeTruthy();
  });
});

describe('RouteErrorBoundary', () => {
  const app = (initial: string) => (
    <MemoryRouter initialEntries={[initial]}>
      <div>
        <Link to='/safe'>go safe</Link>
        <RouteErrorBoundary>
          <Routes>
            <Route path='/boom' element={<Boom />} />
            <Route path='/safe' element={<div>page content</div>} />
          </Routes>
        </RouteErrorBoundary>
      </div>
    </MemoryRouter>
  );

  it('offers a way onward rather than a dead end', () => {
    render(app('/boom'));
    expect(screen.getByText(/didn’t load/i)).toBeTruthy();
    expect(screen.getByText('Go to the home page').getAttribute('href')).toBe('/');
    expect(screen.getByText('Browse all products').getAttribute('href')).toBe('/search');
  });

  it('recovers on navigation — the property the old orphaned stub lacked', () => {
    render(app('/boom'));
    expect(screen.getByText(/didn’t load/i)).toBeTruthy();
    fireEvent.click(screen.getByText('go safe'));
    expect(screen.getByText('page content')).toBeTruthy();
  });

  it('never claims the failure was reported to anyone', () => {
    render(app('/boom'));
    expect(document.body.textContent).not.toMatch(/report|logged|notified|our team/i);
  });
});
