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
      { path: '/admin/user/history', title: 'Search history', wired: true },
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
      { path: '/admin/mall/freight', title: 'Freight templates', wired: true },
      { path: '/admin/mall/store', title: 'Stores', wired: true },
      { path: '/admin/mall/writeoff', title: 'Pickup write-off', wired: true },
      { path: '/admin/mall/article', title: 'Articles', wired: true },
      { path: '/admin/mall/page', title: 'DIY pages', wired: true },
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
      // Wave 12: CJ inventory insight (goods-management insight backend).
      { path: '/admin/goods/categories', title: 'Goods by category', wired: true },
      { path: '/admin/goods/categories/:id', title: 'Category goods', wired: true, hidden: true },
      { path: '/admin/goods/:id/insight', title: 'Goods insight', wired: true, hidden: true },
      { path: '/admin/goods/deal-candidates', title: 'Deal proposals', wired: true },
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
      { path: '/admin/promotion/deal', title: 'Flash Deals', wired: true },
      { path: '/admin/promotion/topic', title: 'Topics', wired: true },
      { path: '/admin/promotion/groupon-rule', title: 'Groupon rules', wired: true },
      { path: '/admin/promotion/groupon-activity', title: 'Groupon activity', wired: true },
      // Wave 6: social-posting ledger + Phase-2 targeting campaigns
      // (promotion-service backends).
      { path: '/admin/promotion/social', title: 'Social posts', wired: true },
      { path: '/admin/promotion/campaign', title: 'Campaigns', wired: true },
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
      // Wave 6: customer-mail outbox (order-service backend).
      { path: '/admin/sys/mail', title: 'Mail outbox', wired: true },
      // Reached from the navbar bell, not the sidebar.
      { path: '/admin/profile', title: 'My profile', wired: true, hidden: true },
    ],
  },
  {
    key: 'affiliate',
    title: 'Affiliate',
    icon: IconTrendingUp,
    children: [
      { path: '/admin/affiliate/promoter', title: 'Promoters', wired: true },
      { path: '/admin/affiliate/promoter/:id/ledger', title: 'Promoter ledger', wired: true, hidden: true },
      { path: '/admin/affiliate/extract', title: 'Withdrawals', wired: true },
    ],
  },
  {
    key: 'config',
    title: 'Config',
    icon: IconSliders,
    children: [
      { path: '/admin/config/mall', title: 'Mall config', wired: true },
      { path: '/admin/config/express', title: 'Express config', wired: true },
      { path: '/admin/config/order', title: 'Order config', wired: true },
      { path: '/admin/config/brokerage', title: 'Brokerage config', wired: true },
      // WeChat is out of scope for this deployment — the wx config group is
      // deliberately not ported (no backend rows). Stays a placeholder.
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

// Wave 5: the affiliate portal's own menu — rendered for ROLE_AFFILIATE
// sessions instead of ADMIN_MENU (see menuForAuthorities). Affiliates never
// see (or reach — PrivateRoute + edge SecurityConfig) the admin tree.
export const AFFILIATE_MENU: MenuGroup[] = [
  {
    key: 'affiliate-portal',
    title: 'Affiliate',
    icon: IconTrendingUp,
    children: [
      { path: '/affiliate/dashboard', title: 'Dashboard', wired: true },
      { path: '/affiliate/links', title: 'My links', wired: true },
      { path: '/affiliate/earnings', title: 'Earnings', wired: true },
      { path: '/affiliate/team', title: 'Team', wired: true },
      { path: '/affiliate/withdraw', title: 'Withdraw', wired: true },
    ],
  },
];

// Role filter: which menu tree a session sees. The admin menu is unchanged
// for admins; affiliates get only their portal group.
export const menuForAuthorities = (authorities: string[]): MenuGroup[] =>
  authorities.includes('ROLE_AFFILIATE') ? AFFILIATE_MENU : ADMIN_MENU;

// Flat list of every leaf — used by the router to register routes and by the
// breadcrumb/tags-view to resolve a path to its title.
export const ALL_LEAVES: MenuLeaf[] = ADMIN_MENU.flatMap(g => g.children);
export const AFFILIATE_LEAVES: MenuLeaf[] = AFFILIATE_MENU.flatMap(g => g.children);

export const titleForPath = (pathname: string): string | undefined => {
  // exact match first
  const exact = ALL_LEAVES.find(l => l.path === pathname) || AFFILIATE_LEAVES.find(l => l.path === pathname);
  if (exact) return exact.title;
  // affiliate admin: per-promoter ledger drill-down
  if (/^\/admin\/affiliate\/promoter\/[^/]+\/ledger$/.test(pathname)) return 'Promoter ledger';
  // dynamic goods leaves: create/edit forms before the ':id' detail fallback
  if (pathname === '/admin/goods/create') return 'Add goods';
  if (/^\/admin\/goods\/[^/]+\/edit$/.test(pathname)) return 'Edit goods';
  // Wave 12 insight leaves — must resolve before the ':id' detail fallback
  // (which would otherwise claim /admin/goods/categories/<id>).
  if (/^\/admin\/goods\/categories\/[^/]+$/.test(pathname)) return 'Category goods';
  if (/^\/admin\/goods\/[^/]+\/insight$/.test(pathname)) return 'Goods insight';
  if (/^\/admin\/goods\/[^/]+$/.test(pathname)) return 'Goods detail';
  // order detail: /admin/mall/order/:id
  if (/^\/admin\/mall\/order\/[^/]+$/.test(pathname)) return 'Order detail';
  // catalog create/edit forms: resolve to "<Section> · New|Edit"
  const form = /^\/admin\/mall\/(brand|category|keyword|issue|freight|store|article|page)\/([^/]+)$/.exec(pathname);
  if (form) {
    const section = {
      brand: 'Brands',
      category: 'Categories',
      keyword: 'Keywords',
      issue: 'Issues',
      freight: 'Freight templates',
      store: 'Stores',
      article: 'Articles',
      page: 'DIY pages',
    }[form[1]];
    return `${section} · ${form[2] === 'create' ? 'New' : 'Edit'}`;
  }
  // promotion create/edit forms
  const promo = /^\/admin\/promotion\/(ad|coupon|deal|groupon-rule|topic)\/([^/]+)(\/issued)?$/.exec(pathname);
  if (promo) {
    const section = { ad: 'Ads', coupon: 'Coupons', deal: 'Flash Deals', 'groupon-rule': 'Groupon rules', topic: 'Topics' }[promo[1]];
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
