import React from 'react';
import { createRoot } from 'react-dom/client';
import { Provider } from 'react-redux';

import 'bootstrap/dist/css/bootstrap.min.css';
import 'bootstrap-icons/font/bootstrap-icons.css';
// Global Amazon-style typography — imported AFTER Bootstrap so it wins.
import 'app/sass/global.scss';

import App from 'app/App';
import store from 'app/config/store';
import { initLocale } from 'app/i18n/locale';
import { initCurrency } from 'app/shared/util/currency';
import { RootErrorBoundary } from 'app/components/commonComponents/ErrorBoundary';

// i18n foundation: English is bundled and active synchronously; this applies an
// earlier explicit choice (cookie / ?lang=) and, once site-config answers, the
// operator's enabled list. Nothing here blocks the first render.
void initLocale();
// Display currency: paints from the last answer if there is one, then asks the edge
// (GET /auth/currency). EUR until it answers; never blocks the first render.
void initCurrency();

const root = createRoot(document.getElementById('root') as HTMLElement);
root.render(
  <Provider store={store}>
    <RootErrorBoundary>
      <App />
    </RootErrorBoundary>
  </Provider>
);