import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { useAppSelector } from 'app/config/store';
import { userApi, isMissingEndpoint } from 'app/shared/api';

/**
 * Favorite / collect toggle, mirroring litemall-vue detail `addCollect`
 * (`/srv/collect/addordelete`, type 0 = goods). Gated on auth — anonymous
 * customers are bounced to /login. Degrades silently if the endpoint isn't
 * live yet (the page still works without favorites).
 */
interface Props {
  goodsId?: number | string;
  initialCollected?: boolean;
}

const CollectButton: React.FC<Props> = ({ goodsId, initialCollected = false }) => {
  const navigate = useNavigate();
  const isAuthenticated = useAppSelector(state => state.customerAuth.data.isAuthenticated);
  const [collected, setCollected] = useState(initialCollected);
  const [busy, setBusy] = useState(false);

  const toggle = async () => {
    if (!isAuthenticated) {
      navigate('/login');
      return;
    }
    if (goodsId == null || busy) return;
    setBusy(true);
    const next = !collected;
    setCollected(next); // optimistic
    try {
      await userApi.collectToggle(0, goodsId);
    } catch (e) {
      if (!isMissingEndpoint(e)) setCollected(!next); // revert on real error
    } finally {
      setBusy(false);
    }
  };

  return (
    <button type='button' className={`lm-pdp__collect${collected ? ' is-on' : ''}`} onClick={toggle} disabled={busy} aria-pressed={collected}>
      <i className={`bi ${collected ? 'bi-heart-fill' : 'bi-heart'}`} /> {collected ? 'Saved' : 'Save'}
    </button>
  );
};

export default CollectButton;
