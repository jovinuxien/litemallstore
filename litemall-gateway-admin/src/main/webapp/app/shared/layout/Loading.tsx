import React from 'react';
//import { Spinner } from 'react-bootstrap'; // Assuming you're using Bootstrap
//import './loading.scss'; // Optional styling

export const Loading = () => (
  <div className='loading-container'>
    {/*  <Spinner animation="border" role="status">
      <span className="visually-hidden">Loading...</span>s
    </Spinner> */}
    <div>Loading...</div>
    <div className='loading-text'>Loading content...</div>
  </div>
);

export default Loading;
