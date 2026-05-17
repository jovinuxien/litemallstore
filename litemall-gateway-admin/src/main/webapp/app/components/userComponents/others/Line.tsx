import React from 'react';

type LineProps = {
  className?: string;
};

const Line: React.FC<LineProps> = props => {
  return (
    <div className={`progress ${props.className}`} style={{ height: 1 }}>
      <div className='progress-bar' role='progressbar' style={{ width: '25%' }} aria-valuenow={25} aria-valuemin={0} aria-valuemax={100} />
    </div>
  );
};
export default Line;
