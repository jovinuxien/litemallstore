import React from 'react';
import { createRoot } from 'react-dom/client';
import { Provider } from 'react-redux';
import { bindActionCreators } from 'redux';

import AppComponent from 'app/App1';
import setupAxiosInterceptors from 'app/config/axios-interceptor';
//import { loadIcons } from 'app/config/icon-loader';
import getStore from 'app/config/store';
//import { registerLocale } from 'app/config/translation';
import ErrorBoundary from 'app/shared/error/error-boundary';
import { clearAdminAuth } from 'app/shared/reducers/admin-auth';

const store = getStore();
//registerLocale(store);

const actions = bindActionCreators({ clearAdminAuth }, store.dispatch);
setupAxiosInterceptors(() => actions.clearAdminAuth());

//loadIcons();

const rootEl = document.getElementById('root');
const root = createRoot(rootEl);

const render = Component =>
  root.render(
    <ErrorBoundary>
      <Provider store={store}>
        <div>
          <Component />
        </div>
      </Provider>
    </ErrorBoundary>
  );

render(AppComponent);
