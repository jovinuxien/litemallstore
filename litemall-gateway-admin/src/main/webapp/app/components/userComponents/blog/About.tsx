import React, { lazy } from 'react';
const Line = lazy(() => import('../others/Line'));

interface AboutProps {
  title: string;
  className?: string;
}

const About: React.FC<AboutProps> = ({ title, className }) => {
  return (
    <div className={`p-4 mb-3 bg-light rounded ${className ? className : ''}`}>
      <h4 className='fst-italic'>{title}</h4>
      <Line className={className} />
      <p className='mb-0'>
        Quis vero phasellus hac nullam, in quam vitae duis adipiscing mauris leo, laoreet eget at quis, ante vestibulum vivamus vel. Sapien lobortis, eget orci
        purus amet pede, consectetur neque risus.
      </p>
    </div>
  );
};
export default About;
