import React, { lazy } from 'react';
const About = lazy(() => import('./About'));
const Follow = lazy(() => import('./Follow'));
const MostViewed = lazy(() => import('./MostViewed'));
const Tags = lazy(() => import('./Tags'));
const Archives = lazy(() => import('./Archives'));

const Widgets = props => {
  return (
    <React.Fragment>
      <About title='About Me' />
      <Follow title='Follow Me' />
      <MostViewed title='Most Viewed' />
      <Tags title='Tags' />
      <Archives title='Archives' />
    </React.Fragment>
  );
};

export default Widgets;
