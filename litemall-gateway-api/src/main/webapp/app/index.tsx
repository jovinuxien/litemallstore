import React from 'react';
import { createRoot } from 'react-dom/client';
import { Provider } from 'react-redux';

import 'bootstrap/dist/css/bootstrap.min.css';
import 'bootstrap-icons/font/bootstrap-icons.css';
// Global Amazon-style typography — imported AFTER Bootstrap so it wins.
import 'app/sass/global.scss';

import App from 'app/App';
import store from 'app/config/store';
import { RootErrorBoundary } from 'app/components/commonComponents/ErrorBoundary';

const root = createRoot(document.getElementById('root') as HTMLElement);
root.render(
  <Provider store={store}>
    <RootErrorBoundary>
      <App />
    </RootErrorBoundary>
  </Provider>
);