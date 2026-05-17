import React, { useState } from 'react';

interface CheckboxProps {
  ranges: [number, number][]; // Initial numeric value for the checkbox.
  onRangeChange: (selectedRanges: [number, number][]) => void; // Function to be called when the checkbox value changes.
}
const Checkbox: React.FC<CheckboxProps> = ({ ranges, onRangeChange }) => {
  const [checkedRanges, setCheckedRanges] = useState<[number, number][]>([]);

  const handleCheckboxChange = (range: [number, number], isChecked: boolean) => {
    const updateRanges = isChecked ? [...checkedRanges, range] : checkedRanges.filter(r => r[0] !== range[0] || r[1] !== range[1]);
    setCheckedRanges(updateRanges);
    onRangeChange(updateRanges);
  };
  return (
    <div className='form-check'>
      {ranges.map((range, index) => (
        <label key={index} style={{ display: 'block', marginBottom: '8px' }}>
          <input
            type='checkbox'
            checked={checkedRanges.some(r => r[0] === range[0] && r[1] === range[1])}
            onChange={e => handleCheckboxChange(range, e.target.checked)}
          />
          Range: {range[0]} - {range[1]}
        </label>
      ))}
    </div>
  );
};

export default Checkbox;
