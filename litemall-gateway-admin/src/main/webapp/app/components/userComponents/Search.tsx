import React from 'react';

const Search: React.FC = () => {
  return (
    <>
      <div className='input-group'>
        <input id='search' name='search' type='text' className='form-control' placeholder='Search...' aria-label='Search input' />
        {/* <label className='visually-hidden' htmlFor='search'></label> */}
      </div>
      <div className='col-md-1'>
        <button type='submit' className='btn btn-primary' id='button-addon'>
          <i className='bi bi-search'></i>
        </button>
      </div>
    </>
  );
};
export default Search;
