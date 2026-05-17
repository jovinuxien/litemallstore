import { CategoryData } from 'app/shared/model/category/category.models';
import React from 'react';
type FilterCategoryProps = {
  subCategoryData: CategoryData[];
};
const FilterCategory: React.FC<FilterCategoryProps> = ({ subCategoryData }) => {
  return (
    <div className='card mb-3 accordion'>
      <div
        className='card-header fw-bold text-uppercase accordion-icon-button'
        data-bs-toggle='collapse'
        data-bs-target='#filterCategory'
        aria-expanded='true'
        aria-controls='filterCategory'
      >
        Categories
      </div>
      <ul className='list-group list-group-flush show' id='filterCategory'>
        {/* {categoryFilterList.subCategoryList.map((category, idx) => (
          <li key={idx} className='nav-item'>
            <Link to={`/category/${category.id}`} className='nav-link'>
              <div className='text-center'>
                <h4>{category.name}</h4>
              </div>
            </Link>
          </li>
        ))} */}
      </ul>
    </div>
  );
};

export default FilterCategory;
