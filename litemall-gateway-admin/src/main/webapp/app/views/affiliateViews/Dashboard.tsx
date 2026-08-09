import { useGetDashboardQuery } from 'app/shared/reducers/private/services/affiliateApi';
import * as React from 'react';
import { Link } from 'react-router-dom';
import { money } from 'app/shared/util/money';

// Affiliate dashboard (Wave 5): the headline sums from
// GET /srv/private/affiliate/dashboard — available (withdrawable
// brokerage_price), frozen, lifetime/this-month earnings and referral counts.
// Self-scoped server-side; nothing here takes an id.


interface CardProps {
  label: string;
  value: React.ReactNode;
  accent?: string;
  hint?: string;
}

const StatCard: React.FC<CardProps> = ({ label, value, accent, hint }) => (
  <div className='col-md-4 col-sm-6 mb-3'>
    <div className='card h-100'>
      <div className='card-body'>
        <div className='text-muted small'>{label}</div>
        <div className='fs-3 fw-semibold' style={accent ? { color: accent } : undefined}>
          {value}
        </div>
        {hint && <div className='form-text text-muted'>{hint}</div>}
      </div>
    </div>
  </div>
);

const AffiliateDashboard: React.FC = () => {
  const { data, isLoading, isError } = useGetDashboardQuery();

  if (isLoading) {
    return (
      <div className='app-container text-center p-5'>
        <span className='spinner-border text-primary' role='status' />
      </div>
    );
  }

  return (
    <div className='app-container'>
      <h5 className='mb-3'>Affiliate dashboard</h5>
      {isError && <div className='alert alert-danger'>Failed to load your dashboard. Please try again.</div>}
      <div className='row'>
        <StatCard label='Available balance' value={money(data?.available)} accent='#67c23a' hint='Withdrawable commission' />
        <StatCard label='Frozen commission' value={money(data?.frozenSum)} accent='#e6a23c' hint='Unlocks after the freeze window' />
        <StatCard label='This month' value={money(data?.thisMonth)} />
        <StatCard label='Lifetime earned' value={money(data?.lifetimeEarned)} />
        <StatCard label='People referred' value={data?.spreadCount ?? '—'} hint='Users registered with your invite' />
        <StatCard label='Referred orders' value={data?.referredOrders ?? '—'} />
      </div>
      <div className='mt-2'>
        <Link className='btn btn-primary me-2' to='/affiliate/links'>
          Get my invite links
        </Link>
        <Link className='btn btn-outline-primary' to='/affiliate/withdraw'>
          Withdraw
        </Link>
      </div>
    </div>
  );
};

export default AffiliateDashboard;
