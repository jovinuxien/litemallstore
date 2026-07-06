import * as React from 'react';
import { useLocation } from 'react-router-dom';
import { IconTool } from './icons';
import { titleForPath } from './menu.config';

// Placeholder for menu items that exist in the upstream litemall-admin but whose
// backend lives in another service in this microservice split (User / Mall /
// Promotion / Sys / Config / Stat). Keeps full menu fidelity without faking
// data; each is a documented follow-up for the owning worktree.
const NotAvailable: React.FC = () => {
  const { pathname } = useLocation();
  const title = titleForPath(pathname) || 'This section';

  return (
    <div className='app-container'>
      <div className='lm-placeholder'>
        <div className='lm-placeholder-icon'>
          <IconTool />
        </div>
        <h3>{title}</h3>
        <p>This section is part of the litemall admin but is served by another module in this deployment.</p>
        <div className='lm-placeholder-note'>
          Not enabled in <code>litemall-gateway-admin</code>. The backend for this page lives in the owning service
          (order / user / promotion / system / config / stat) and is tracked as a follow-up for that worktree.
        </div>
      </div>
    </div>
  );
};

export default NotAvailable;
