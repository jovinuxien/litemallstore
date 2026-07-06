import * as React from 'react';
import { useLocation } from 'react-router-dom';
import { ADMIN_MENU, titleForPath } from './menu.config';

// Breadcrumb derived from the menu config (mirrors upstream Breadcrumb.vue,
// which reads the matched route chain). Shows: <group> / <leaf>.
const Breadcrumb: React.FC = () => {
  const { pathname } = useLocation();

  const leafTitle = titleForPath(pathname);
  const group = ADMIN_MENU.find(g =>
    g.children.some(c => c.path === pathname || (c.path === '/admin/goods' && /^\/admin\/goods(\/|$)/.test(pathname)))
  );

  const segments: string[] = [];
  if (group && group.title !== leafTitle) segments.push(group.title);
  if (leafTitle) segments.push(leafTitle);
  if (segments.length === 0) segments.push('Dashboard');

  return (
    <div className='breadcrumb-container'>
      {segments.map((s, i) => {
        const last = i === segments.length - 1;
        return (
          <React.Fragment key={s}>
            <span className={`breadcrumb-item${last ? ' is-last' : ''}`}>{s}</span>
            {!last && <span className='breadcrumb-sep'>/</span>}
          </React.Fragment>
        );
      })}
    </div>
  );
};

export default Breadcrumb;
