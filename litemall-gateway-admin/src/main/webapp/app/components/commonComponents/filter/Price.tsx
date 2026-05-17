import React, { useState } from 'react';

interface FilterPriceProps {
  onFilterChange: (range: [number, number] | null) => void;
}

const FilterPrice: React.FC<FilterPriceProps> = ({ onFilterChange }) => {
  //const [selectedRanges, setSelectedRanges] = useState<[number, number][]>([]);
  const [selectedRange, setSelectedRange] = useState<[number, number] | null>(null);

  const priceRanges: [number, number][] = [
    [0, 50],
    [51, 100],
    [101, 200],
    [201, 500],
  ];
  /*  const handlePriceRange = (range: [number, number][]) => {
    setSelectedRanges(range);
    console.log('the selected range is ', range);
    onFilterChange(range);
  }; */

  const handlePriceRangeChange = (range: [number, number]) => {
    if (selectedRange && selectedRange[0] === range[0] && selectedRange[1] === range[1]) {
      setSelectedRange(null);
      onFilterChange(null);
    } else {
      setSelectedRange(range);
      onFilterChange(range);
    }
  };

  return (
    <div className='card mb-3'>
      <div
        className='card-header fw-bold text-uppercase accordion-icon-button'
        data-bs-toggle='collapse'
        data-bs-target='#filterPrice'
        aria-expanded='true'
        aria-controls='filterPrice'
      >
        Price
      </div>
      <ul className='list-group list-group-flush show' id='filterPrice'>
        {priceRanges.map((range, index) => (
          <li className='list-group-item' key={index}>
            <div className='form-check'>
              <input
                className='form-check-input'
                type='checkbox'
                id={`priceRange${index}`}
                /* checked={selectedRange && selectedRange[0] === range[0] && selectedRange[1] === range[1]}
                onChange={() => handlePriceRangeChange(range)} */
                checked={selectedRange !== null && selectedRange[0] === range[0] && selectedRange[1] === range[1]}
                onChange={() => handlePriceRangeChange(range)}
              />
              <label className='form-check-label' htmlFor={`priceRange${index}`}>
                ${range[0]} - ${range[1]}
              </label>
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
};

export default FilterPrice;
