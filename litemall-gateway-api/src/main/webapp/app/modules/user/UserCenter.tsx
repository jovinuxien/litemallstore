import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import { useAppSelector } from 'app/config/store';
import { isMissingEndpoint, userApi } from 'app/shared/api';
import './user.scss';

interface UserIndex {
  nickName?: string;
  avatar?: string;
  mobile?: string;
  order?: { unpaid?: number; unship?: number; unrecv?: number; uncomment?: number };
}

const MENU = [
  { to: '/orders', icon: 'bi-box-seam', label: 'My orders' },
  { to: '/refunds', icon: 'bi-arrow-counterclockwise', label: 'After-sales' },
  { to: '/user/favorites', icon: 'bi-heart', label: 'Favorites' },
  { to: '/user/footprint', icon: 'bi-clock-history', label: 'Footprint' },
  { to: '/user/coupons', icon: 'bi-ticket-perforated', label: 'Coupons' },
  { to: '/user/address', icon: 'bi-geo-alt', label: 'Addresses' },
  { to: '/user/profile', icon: 'bi-person-gear', label: 'Profile' },
  { to: '/user/feedback', icon: 'bi-chat-dots', label: 'Feedback' },
];

/**
 * Customer account hub, modelled on litemall-vue `user/tabbar-user`: profile
 * header + order-status shortcuts + a grid of account sections. Profile/stats
 * come from `/srv/user/index`; the hub still renders if that's not live.
 */
const UserCenter: React.FC = () => {
  const auth = useAppSelector(state => state.customerAuth.data.userInfo);
  const [info, setInfo] = useState<UserIndex | null>(null);

  useEffect(() => {
    userApi
      .index()
      .then(d => setInfo((d as UserIndex) ?? null))
      .catch(e => {
        if (!isMissingEndpoint(e)) setInfo(null);
      });
  }, []);

  const nickName = info?.nickName ?? auth?.nickName ?? 'My account';
  const avatar = info?.avatar ?? auth?.avatarUrl;

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
            <span>{m.label}</span>
          </Link>
        ))}
      </div>
    </div>
  );
};

export default UserCenter;
