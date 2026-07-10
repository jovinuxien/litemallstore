import {
  IconBox,
  IconCmp,
  IconGrid,
  IconSettings,
  IconShoppingBag,
  IconSliders,
  IconTag,
  IconTrendingUp,
  IconUsers,
} from './icons';

// Navigation tree mirroring the upstream litemall-admin router
// (src/router/index.js): the same groups/children, in the same order, so the
// sidebar reads 1:1 against the original Element admin. Each leaf carries
// `wired` — true only for the routes that actually have a backend in this
// microservice split (goods list/detail + the order-stats dashboard). Every
// other leaf renders the styled "not available in this deployment" placeholder
// (NotAvailable) and is a documented follow-up for the owning worktree.

export interface MenuLeaf {
  path: string; // absolute route under the SPA, e.g. /admin/goods
  title: string;
  wired?: boolean; // has a real view/backend here
  hidden?: boolean; // reachable by route but not shown in the sidebar
}

export interface MenuGroup {
  key: string;
  title: string;
  icon: IconCmp;
  children: MenuLeaf[];
}

export const ADMIN_MENU: MenuGroup[] = [
  {
    key: 'dashboard',
    title: 'Dashboard',
    icon: IconGrid,
    children: [{ path: '/admin/dashboard', title: 'Dashboard', wired: true }],
  },
  {
    key: 'user',
    title: 'User',
    icon: IconUsers,
    children: [
      { path: '/admin/user/user', title: 'Users', wired: true },
      { path: '/admin/user/address', title: 'Addresses', wired: true },
      { path: '/admin/user/collect', title: 'Collections', wired: true },
      { path: '/admin/user/footprint', title: 'Footprints', wired: true },
      { path: '/admin/user/history', title: 'Search history' },
      { path: '/admin/user/feedback', title: 'Feedback', wired: true },
    ],
  },
  {
    key: 'mall',
    title: 'Mall',
    icon: IconShoppingBag,
    children: [
      { path: '/admin/mall/region', title: 'Regions' },
      { path: '/admin/mall/brand', title: 'Brands', wired: true },
      { path: '/admin/mall/category', title: 'Categories', wired: true },
      { path: '/admin/mall/order', title: 'Orders', wired: true },
      { path: '/admin/mall/order/:id', title: 'Order detail', wired: true, hidden: true },
      { path: '/admin/mall/aftersale', title: 'After-sale', wired: true },
      { path: '/admin/mall/issue', title: 'Issues', wired: true },
      { path: '/admin/mall/keyword', title: 'Keywords', wired: true },
    ],
  },
  {
    key: 'goods',
    title: 'Goods',
    icon: IconBox,
    children: [
      { path: '/admin/goods', title: 'Goods list', wired: true },
      { path: '/admin/goods/:id', title: 'Goods detail', wired: true, hidden: true },
      { path: '/admin/goods/:id/edit', title: 'Edit goods', wired: true, hidden: true },
      { path: '/admin/goods/create', title: 'Add goods', wired: true },
      { path: '/admin/goods/comment', title: 'Comments', wired: true },
    ],
  },
  {
    key: 'promotion',
    title: 'Promotion',
    icon: IconTag,
    children: [
      { path: '/admin/promotion/ad', title: 'Ads', wired: true },
      { path: '/admin/promotion/coupon', title: 'Coupons', wired: true },
      { path: '/admin/promotion/topic', title: 'Topics' },
      { path: '/admin/promotion/groupon-rule', title: 'Groupon rules', wired: true },
      { path: '/admin/promotion/groupon-activity', title: 'Groupon activity', wired: true },
    ],
  },
  {
    key: 'sys',
    title: 'System',
    icon: IconSettings,
    children: [
      { path: '/admin/sys/admin', title: 'Admins', wired: true },
      { path: '/admin/sys/notice', title: 'Notices', wired: true },
      { path: '/admin/sys/log', title: 'Logs', wired: true },
      { path: '/admin/sys/role', title: 'Roles', wired: true },
      { path: '/admin/sys/os', title: 'Storage', wired: true },
    ],
  },
  {
    key: 'config',
    title: 'Config',
    icon: IconSliders,
    children: [
      { path: '/admin/config/mall', title: 'Mall config' },
      { path: '/admin/config/express', title: 'Express config' },
      { path: '/admin/config/order', title: 'Order config' },
      { path: '/admin/config/wx', title: 'WeChat config' },
    ],
  },
  {
    key: 'stat',
    title: 'Statistics',
    icon: IconTrendingUp,
    children: [
      { path: '/admin/stat/user', title: 'User stats', wired: true },
      { path: '/admin/stat/order', title: 'Order stats', wired: true },
      { path: '/admin/stat/goods', title: 'Goods stats', wired: true },
    ],
  },
];

// Flat list of every leaf — used by the router to register routes and by the
// breadcrumb/tags-view to resolve a path to its title.
export const ALL_LEAVES: MenuLeaf[] = ADMIN_MENU.flatMap(g => g.children);

export const titleForPath = (pathname: string): string | undefined => {
  // exact match first
  const exact = ALL_LEAVES.find(l => l.path === pathname);
  if (exact) return exact.title;
  // dynamic goods leaves: create/edit forms before the ':id' detail fallback
  if (pathname === '/admin/goods/create') return 'Add goods';
  if (/^\/admin\/goods\/[^/]+\/edit$/.test(pathname)) return 'Edit goods';
  if (/^\/admin\/goods\/[^/]+$/.test(pathname)) return 'Goods detail';
  // order detail: /admin/mall/order/:id
  if (/^\/admin\/mall\/order\/[^/]+$/.test(pathname)) return 'Order detail';
  // catalog create/edit forms: resolve to "<Section> · New|Edit"
  const form = /^\/admin\/mall\/(brand|category|keyword|issue)\/([^/]+)$/.exec(pathname);
  if (form) {
    const section = { brand: 'Brands', category: 'Categories', keyword: 'Keywords', issue: 'Issues' }[form[1]];
    return `${section} · ${form[2] === 'create' ? 'New' : 'Edit'}`;
  }
  // promotion create/edit forms
  const promo = /^\/admin\/promotion\/(ad|coupon|groupon-rule)\/([^/]+)(\/issued)?$/.exec(pathname);
  if (promo) {
    const section = { ad: 'Ads', coupon: 'Coupons', 'groupon-rule': 'Groupon rules' }[promo[1]];
    if (promo[3]) return `${section} · Issued`;
    return `${section} · ${promo[2] === 'create' ? 'New' : 'Edit'}`;
  }
  // system create/edit forms
  const sys = /^\/admin\/sys\/(admin|notice|role)\/([^/]+)$/.exec(pathname);
  if (sys) {
    const section = { admin: 'Admins', notice: 'Notices', role: 'Roles' }[sys[1]];
    return `${section} · ${sys[2] === 'create' ? 'New' : 'Edit'}`;
  }
  return undefined;
};
