import * as React from 'react';
import { useNavigate } from 'react-router-dom';
import { Collapse } from 'reactstrap';
import { IconChevronRight } from './icons';
import { MenuGroup } from './menu.config';

// One sidebar group. Mirrors upstream SidebarItem.vue: a group with a single
// visible leaf whose title matches the group renders as a flat menu item;
// otherwise it's a collapsible submenu holding its child leaves. Unwired leaves
// stay navigable (they resolve to the NotAvailable placeholder) — the original
// shows every node, so we do too.

interface Props {
  group: MenuGroup;
  activePath: string;
  collapsed: boolean;
}

const isLeafActive = (leafPath: string, activePath: string): boolean =>
  activePath === leafPath || (leafPath === '/admin/goods' && /^\/admin\/goods(\/|$)/.test(activePath));

const SidebarItem: React.FC<Props> = ({ group, activePath, collapsed }) => {
  const navigate = useNavigate();
  const visibleChildren = group.children.filter(c => !c.hidden);
  const Icon = group.icon;

  const groupActive = visibleChildren.some(c => isLeafActive(c.path, activePath));
  const [open, setOpen] = React.useState<boolean>(groupActive);

  React.useEffect(() => {
    if (groupActive) setOpen(true);
  }, [groupActive]);

  // single-leaf group (e.g. Dashboard) → flat item
  if (visibleChildren.length === 1 && visibleChildren[0].title === group.title) {
    const leaf = visibleChildren[0];
    return (
      <li className='menu-wrapper'>
        <a
          className={`menu-item${isLeafActive(leaf.path, activePath) ? ' is-active' : ''}`}
          onClick={() => navigate(leaf.path)}
          role='button'
        >
          <Icon className='menu-icon' />
          <span>{group.title}</span>
        </a>
      </li>
    );
  }

  return (
    <li className='menu-wrapper'>
      <div
        className={`submenu-title${open ? ' is-open' : ''}`}
        onClick={() => !collapsed && setOpen(o => !o)}
        role='button'
        aria-expanded={open}
      >
        <Icon className='menu-icon' />
        <span>{group.title}</span>
        <IconChevronRight className='submenu-arrow' />
      </div>
      <Collapse isOpen={open && !collapsed}>
        <ul className='el-menu nest-menu'>
          {visibleChildren.map(leaf => (
            <li className='menu-wrapper' key={leaf.path}>
              <a
                className={`menu-item${isLeafActive(leaf.path, activePath) ? ' is-active' : ''}`}
                onClick={() => navigate(leaf.path)}
                role='button'
              >
                <span>{leaf.title}</span>
              </a>
            </li>
          ))}
        </ul>
      </Collapse>
    </li>
  );
};

export default SidebarItem;
