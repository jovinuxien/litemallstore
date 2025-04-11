/* import React from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';

const app = <App />;
const appDiv = document.getElementById('app');

appDiv ? createRoot(appDiv).render(app) : console.error('Element with id "root" not found in the document.');
 */
import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import store from './config/store';
//import "./index.css";
//import rootReducer from './reducers';
import { Provider } from 'react-redux';
import * as serviceWorker from './serviceWorker';
import ErrorBoundary from './views/commonViews/boundaryerror/ErrorBoundary';
//const persistedState = loadState();
//const store = createStore(rootReducer, persistedState);

/* store.subscribe(() => {
  saveState(store);
}); */
const root = document.getElementById('root');
if (root) {
  ReactDOM.createRoot(root).render(
    <ErrorBoundary>
      <Provider store={store}>
        <App />
      </Provider>
    </ErrorBoundary>
  );
} else {
  console.error('Element with id "root" not found in the document.');
}
/* root.render(
  <Provider store={store}>
    <App />
  </Provider>
); */
//root.render(<App />);
// If you want your app to work offline and load faster, you can change
// unregister() to register() below. Note this comes with some pitfalls.
// Learn more about service workers: https://bit.ly/CRA-PWA
serviceWorker.register();
