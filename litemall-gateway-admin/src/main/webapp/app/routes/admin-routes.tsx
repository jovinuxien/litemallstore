import AdminGoodsList from 'app/views/adminViews/adminModule/Goods/AdminGoodsList';
import GoodsDetail from 'app/views/adminViews/adminModule/Goods/GoodsDetail/GoodsDetail';
import GoodsForm from 'app/views/adminViews/adminModule/Goods/GoodsForm';
import Dashboard from 'app/views/adminViews/adminModule/Dashboard/Dashboard';
import StatPage from 'app/views/adminViews/adminModule/Stat/StatPage';
import UserList from 'app/views/adminViews/adminModule/User/UserList';
import AddressList from 'app/views/adminViews/adminModule/User/AddressList';
import CollectList from 'app/views/adminViews/adminModule/User/CollectList';
import FootprintList from 'app/views/adminViews/adminModule/User/FootprintList';
import FeedbackList from 'app/views/adminViews/adminModule/User/FeedbackList';
import AftersaleList from 'app/views/adminViews/adminModule/Order/AftersaleList';
import BrandList from 'app/views/adminViews/adminModule/Brand/BrandList';
import BrandForm from 'app/views/adminViews/adminModule/Brand/BrandForm';
import CategoryList from 'app/views/adminViews/adminModule/Category/CategoryList';
import CategoryForm from 'app/views/adminViews/adminModule/Category/CategoryForm';
import KeywordList from 'app/views/adminViews/adminModule/Keyword/KeywordList';
import KeywordForm from 'app/views/adminViews/adminModule/Keyword/KeywordForm';
import IssueList from 'app/views/adminViews/adminModule/Issue/IssueList';
import IssueForm from 'app/views/adminViews/adminModule/Issue/IssueForm';
import CommentList from 'app/views/adminViews/adminModule/Comment/CommentList';
import OrderList from 'app/views/adminViews/adminModule/Order/OrderList';
import OrderDetail from 'app/views/adminViews/adminModule/Order/OrderDetail';
import AdList from 'app/views/adminViews/adminModule/Ad/AdList';
import AdForm from 'app/views/adminViews/adminModule/Ad/AdForm';
import CouponList from 'app/views/adminViews/adminModule/Coupon/CouponList';
import CouponForm from 'app/views/adminViews/adminModule/Coupon/CouponForm';
import CouponUserList from 'app/views/adminViews/adminModule/Coupon/CouponUserList';
import DealList from 'app/views/adminViews/adminModule/Deal/DealList';
import DealForm from 'app/views/adminViews/adminModule/Deal/DealForm';
import GrouponRuleList from 'app/views/adminViews/adminModule/Groupon/GrouponRuleList';
import GrouponRuleForm from 'app/views/adminViews/adminModule/Groupon/GrouponRuleForm';
import GrouponActivityList from 'app/views/adminViews/adminModule/Groupon/GrouponActivityList';
import AdminAccountList from 'app/views/adminViews/adminModule/Sys/AdminAccountList';
import AdminAccountForm from 'app/views/adminViews/adminModule/Sys/AdminAccountForm';
import NoticeList from 'app/views/adminViews/adminModule/Sys/NoticeList';
import NoticeForm from 'app/views/adminViews/adminModule/Sys/NoticeForm';
import LogList from 'app/views/adminViews/adminModule/Sys/LogList';
import RoleList from 'app/views/adminViews/adminModule/Sys/RoleList';
import RoleForm from 'app/views/adminViews/adminModule/Sys/RoleForm';
import StorageList from 'app/views/adminViews/adminModule/Sys/StorageList';
import FreightTemplateList from 'app/views/adminViews/adminModule/Freight/FreightTemplateList';
import FreightTemplateForm from 'app/views/adminViews/adminModule/Freight/FreightTemplateForm';
import StoreList from 'app/views/adminViews/adminModule/Store/StoreList';
import StoreForm from 'app/views/adminViews/adminModule/Store/StoreForm';
import WriteOffConsole from 'app/views/adminViews/adminModule/Store/WriteOffConsole';
import ArticleList from 'app/views/adminViews/adminModule/Article/ArticleList';
import ArticleForm from 'app/views/adminViews/adminModule/Article/ArticleForm';
import PageList from 'app/views/adminViews/adminModule/Page/PageList';
import PageEditor from 'app/views/adminViews/adminModule/Page/PageEditor';
import TopicList from 'app/views/adminViews/adminModule/Topic/TopicList';
import TopicForm from 'app/views/adminViews/adminModule/Topic/TopicForm';
import HistoryList from 'app/views/adminViews/adminModule/User/HistoryList';
import ConfigMall from 'app/views/adminViews/adminModule/Sys/ConfigMall';
import ConfigExpress from 'app/views/adminViews/adminModule/Sys/ConfigExpress';
import ConfigOrder from 'app/views/adminViews/adminModule/Sys/ConfigOrder';
import ConfigBrokerage from 'app/views/adminViews/adminModule/Sys/ConfigBrokerage';
import PromoterList from 'app/views/adminViews/adminModule/Affiliate/PromoterList';
import PromoterLedger from 'app/views/adminViews/adminModule/Affiliate/PromoterLedger';
import ExtractList from 'app/views/adminViews/adminModule/Affiliate/ExtractList';
import ProfilePage from 'app/views/adminViews/adminModule/Profile/ProfilePage';
import SocialPostList from 'app/views/adminViews/adminModule/Social/SocialPostList';
import PostizPublish from 'app/views/adminViews/adminModule/Postiz/PostizPublish';
import CategoryInsightList from 'app/views/adminViews/adminModule/Insight/CategoryInsightList';
import CategoryGoodsList from 'app/views/adminViews/adminModule/Insight/CategoryGoodsList';
import GoodsInsight from 'app/views/adminViews/adminModule/Insight/GoodsInsight';
import DealCandidateList from 'app/views/adminViews/adminModule/Insight/DealCandidateList';
import PromoCandidateList from 'app/views/adminViews/adminModule/Insight/PromoCandidateList';
import RetireCandidateList from 'app/views/adminViews/adminModule/Insight/RetireCandidateList';
import ArrivalsList from 'app/views/adminViews/adminModule/Insight/ArrivalsList';
import CampaignList from 'app/views/adminViews/adminModule/Campaign/CampaignList';
import MailOutboxList from 'app/views/adminViews/adminModule/Mail/MailOutboxList';
import AdminLayout from 'app/shared/layout/admin/AdminLayout';
import NotAvailable from 'app/shared/layout/admin/NotAvailable';
import { ALL_LEAVES } from 'app/shared/layout/admin/menu.config';
import React from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';

// admin-routes.tsx — mounted under `/admin/*` behind the ADMIN PrivateRoute.
// Everything renders inside the AdminLayout shell (dark sidebar + navbar +
// tags-view). The wired pages (dashboard, goods list, goods detail) use real
// views; every other upstream menu leaf renders the NotAvailable placeholder
// so the menu keeps full fidelity without faking data. Each call still runs as
// an authenticated admin (Bearer admin JWT) via the existing slices/api.

const ADMIN_PREFIX = '/admin/';

// Unwired upstream leaves (User/Mall/Promotion/Sys/Config/Stat + goods
// create/comment) → placeholder. Derived from the single menu config so the
// route table and the sidebar can never drift apart.
const placeholderLeaves = ALL_LEAVES.filter(l => !l.wired && l.path.startsWith(ADMIN_PREFIX) && !l.path.includes(':'));

export const AdminRoutes = () => (
  <Routes>
    <Route element={<AdminLayout />}>
      <Route index element={<Navigate to='dashboard' replace />} />
      <Route path='dashboard' element={<Dashboard />} />
      <Route path='goods' element={<AdminGoodsList />} />
      <Route path='goods/comment' element={<CommentList />} />
      {/* Wave 12: CJ inventory insight — static segments before ':id' routes */}
      <Route path='goods/categories' element={<CategoryInsightList />} />
      <Route path='goods/categories/:id' element={<CategoryGoodsList />} />
      <Route path='goods/deal-candidates' element={<DealCandidateList />} />
      {/* Wave 19: coupon/groupon promo suggestions — also static before ':id' */}
      <Route path='goods/promo-candidates' element={<PromoCandidateList />} />
      {/* Wave 14: inventory governance — also static before ':id' */}
      <Route path='goods/retire' element={<RetireCandidateList />} />
      <Route path='goods/arrivals' element={<ArrivalsList />} />
      {/* static 'create' wins over the ':id' detail route in v6 ranking */}
      <Route path='goods/create' element={<GoodsForm />} />
      <Route path='goods/:id/edit' element={<GoodsForm />} />
      <Route path='goods/:id/insight' element={<GoodsInsight />} />
      <Route path='goods/:id' element={<GoodsDetail />} />
      <Route path='user/user' element={<UserList />} />
      <Route path='user/address' element={<AddressList />} />
      <Route path='user/collect' element={<CollectList />} />
      <Route path='user/footprint' element={<FootprintList />} />
      <Route path='user/feedback' element={<FeedbackList />} />
      {/* Wave 4: search history (goods-management admin) */}
      <Route path='user/history' element={<HistoryList />} />
      <Route path='stat/user' element={<StatPage kind='user' />} />
      <Route path='stat/order' element={<StatPage kind='order' />} />
      <Route path='stat/goods' element={<StatPage kind='goods' />} />
      <Route path='mall/brand' element={<BrandList />} />
      <Route path='mall/brand/create' element={<BrandForm />} />
      <Route path='mall/brand/:id' element={<BrandForm />} />
      <Route path='mall/category' element={<CategoryList />} />
      <Route path='mall/category/create' element={<CategoryForm />} />
      <Route path='mall/category/:id' element={<CategoryForm />} />
      <Route path='mall/keyword' element={<KeywordList />} />
      <Route path='mall/keyword/create' element={<KeywordForm />} />
      <Route path='mall/keyword/:id' element={<KeywordForm />} />
      <Route path='mall/issue' element={<IssueList />} />
      <Route path='mall/issue/create' element={<IssueForm />} />
      <Route path='mall/issue/:id' element={<IssueForm />} />
      <Route path='mall/order' element={<OrderList />} />
      <Route path='mall/order/:id' element={<OrderDetail />} />
      <Route path='mall/aftersale' element={<AftersaleList />} />
      {/* Wave 4: freight templates + stores/write-off (order-service backends) */}
      <Route path='mall/freight' element={<FreightTemplateList />} />
      <Route path='mall/freight/create' element={<FreightTemplateForm />} />
      <Route path='mall/freight/:id' element={<FreightTemplateForm />} />
      <Route path='mall/store' element={<StoreList />} />
      <Route path='mall/store/create' element={<StoreForm />} />
      <Route path='mall/store/:id' element={<StoreForm />} />
      <Route path='mall/writeoff' element={<WriteOffConsole />} />
      {/* Wave 4: article CMS + DIY pages (goods-management content subdomain) */}
      <Route path='mall/article' element={<ArticleList />} />
      <Route path='mall/article/create' element={<ArticleForm />} />
      <Route path='mall/article/:id' element={<ArticleForm />} />
      <Route path='mall/page' element={<PageList />} />
      <Route path='mall/page/create' element={<PageEditor />} />
      <Route path='mall/page/:id' element={<PageEditor />} />
      {/* Promotion: ads (edge-hosted) / coupons + group-buy (promotion-service) */}
      <Route path='promotion/ad' element={<AdList />} />
      <Route path='promotion/ad/create' element={<AdForm />} />
      <Route path='promotion/ad/:id' element={<AdForm />} />
      <Route path='promotion/coupon' element={<CouponList />} />
      <Route path='promotion/coupon/create' element={<CouponForm />} />
      <Route path='promotion/coupon/:id/issued' element={<CouponUserList />} />
      <Route path='promotion/coupon/:id' element={<CouponForm />} />
      {/* Flash deals (goods-management admin) */}
      <Route path='promotion/deal' element={<DealList />} />
      <Route path='promotion/deal/create' element={<DealForm />} />
      <Route path='promotion/deal/:id' element={<DealForm />} />
      <Route path='promotion/groupon-rule' element={<GrouponRuleList />} />
      <Route path='promotion/groupon-rule/create' element={<GrouponRuleForm />} />
      <Route path='promotion/groupon-rule/:id' element={<GrouponRuleForm />} />
      <Route path='promotion/groupon-activity' element={<GrouponActivityList />} />
      {/* Wave 4: topics (goods-management admin CRUD) */}
      <Route path='promotion/topic' element={<TopicList />} />
      <Route path='promotion/topic/create' element={<TopicForm />} />
      <Route path='promotion/topic/:id' element={<TopicForm />} />
      {/* Wave 6: social-posting ledger + targeting campaigns (promotion-service) */}
      <Route path='promotion/social' element={<SocialPostList />} />
      <Route path='promotion/campaign' element={<CampaignList />} />
      {/* Wave 17: Postiz product-post scheduling (promotion-service backend) */}
      <Route path='promotion/postiz' element={<PostizPublish />} />
      {/* System: admins / notices / logs / roles / storage */}
      <Route path='sys/admin' element={<AdminAccountList />} />
      <Route path='sys/admin/create' element={<AdminAccountForm />} />
      <Route path='sys/admin/:id' element={<AdminAccountForm />} />
      <Route path='sys/notice' element={<NoticeList />} />
      <Route path='sys/notice/create' element={<NoticeForm />} />
      <Route path='sys/notice/:id' element={<NoticeForm />} />
      <Route path='sys/log' element={<LogList />} />
      <Route path='sys/role' element={<RoleList />} />
      <Route path='sys/role/create' element={<RoleForm />} />
      <Route path='sys/role/:id' element={<RoleForm />} />
      <Route path='sys/os' element={<StorageList />} />
      {/* Wave 6: customer-mail outbox (order-service backend) */}
      <Route path='sys/mail' element={<MailOutboxList />} />
      {/* Wave 5: affiliate program administration */}
      <Route path='affiliate/promoter' element={<PromoterList />} />
      <Route path='affiliate/promoter/:id/ledger' element={<PromoterLedger />} />
      <Route path='affiliate/extract' element={<ExtractList />} />
      {/* Wave 4: system config (edge-hosted) + own profile/notice inbox */}
      <Route path='config/mall' element={<ConfigMall />} />
      <Route path='config/express' element={<ConfigExpress />} />
      <Route path='config/order' element={<ConfigOrder />} />
      {/* Wave 5: brokerage settings (rows seeded by order's migration) */}
      <Route path='config/brokerage' element={<ConfigBrokerage />} />
      <Route path='profile' element={<ProfilePage />} />
      {placeholderLeaves.map(leaf => (
        <Route key={leaf.path} path={leaf.path.slice(ADMIN_PREFIX.length)} element={<NotAvailable />} />
      ))}
      <Route path='*' element={<NotAvailable />} />
    </Route>
  </Routes>
);
