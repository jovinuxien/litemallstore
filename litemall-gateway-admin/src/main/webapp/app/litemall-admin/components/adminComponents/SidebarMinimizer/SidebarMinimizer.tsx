import * as React from 'react';

interface SidebarMinimizerProps {
  onClick: () => void;
}
const SidebarMinimizer: React.FC<SidebarMinimizerProps> = ({ onClick }) => {
  const sidebarMinimize = () => {
    document.body.classList.toggle('sidebar-minimized');
  };

  const brandMinimize = () => {
    document.body.classList.toggle('brand-minimized');
  };

  const handleClick = () => {
    sidebarMinimize();
    brandMinimize();
  };

  return <button className='sidebar-minimizer' type='button' onClick={handleClick} />;
};

export default SidebarMinimizer;
