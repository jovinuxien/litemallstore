import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import { useAppSelector } from 'app/config/store';
import { useTranslation } from 'app/i18n';
import { AccountInfo, authApi } from 'app/shared/api';
import './user.scss';

const MENU = [
  { to: '/orders', icon: 'bi-box-seam', key: 'orders' },
  { to: '/refunds', icon: 'bi-arrow-counterclockwise', key: 'aftersales' },
  { to: '/user/favorites', icon: 'bi-heart', key: 'favorites' },
  { to: '/user/footprint', icon: 'bi-clock-history', key: 'footprint' },
  { to: '/user/coupons', icon: 'bi-ticket-perforated', key: 'coupons' },
  { to: '/user/address', icon: 'bi-geo-alt', key: 'addresses' },
  { to: '/user/profile', icon: 'bi-person-gear', key: 'profile' },
  { to: '/reset', icon: 'bi-shield-lock', key: 'password' },
  { to: '/user/feedback', icon: 'bi-chat-dots', key: 'feedback' },
];

/**
 * Customer account hub, modelled on litemall-vue `user/tabbar-user`: profile
 * header + a grid of account sections. Account info comes from the auth edge
 * (`GET /auth/me`, Wave 4 Task A); the hub still renders from the cached login
 * userInfo when that call fails.
 */
const UserCenter: React.FC = () => {
  const { t } = useTranslation('user');
  const auth = useAppSelector(state => state.customerAuth.data.userInfo);
  const [info, setInfo] = useState<AccountInfo | null>(null);

  useEffect(() => {
    authApi
      .me()
      .then(env => setInfo(env.errno === 0 ? env.data : null))
      .catch(() => setInfo(null));
  }, []);

  const nickName = info?.nickName ?? auth?.nickName ?? t('center.myAccount');
  const avatar = info?.avatarUrl ?? auth?.avatarUrl;

  return (
    <div className='container my-4 lm-user'>
      <div className='lm-user__header'>
        <div className='lm-user__avatar'>{avatar ? <img src={avatar} alt='' /> : <i className='bi bi-person-circle' />}</div>
        <div>
          <div className='lm-user__name'>{nickName}</div>
          {info?.mobile && <div className='text-muted small'>{info.mobile}</div>}
        </div>
      </div>

      <div className='lm-user__grid'>
        {MENU.map(m => (
          <Link key={m.to} to={m.to} className='lm-user__tile'>
            <i className={`bi ${m.icon}`} />
            <span>{t(`center.menu.${m.key}`)}</span>
          </Link>
        ))}
      </div>
    </div>
  );
};

export default UserCenter;
