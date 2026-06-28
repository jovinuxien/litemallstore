import React from 'react';
import { Link } from 'react-router-dom';

import './storefront.scss';

/**
 * Storefront template kit — reusable React pieces that reproduce litemall-vue's
 * order/user design motifs (cell-group, goods line-card, money summary, sticky
 * submit-bar, status tabs, result panel, address/coupon cards), themed with this
 * SPA's Teal & Coral `--lm-*` tokens. Used by the cart/checkout/order/user views
 * so they share one consistent layout language.
 */

// --------------------------------------------------------------------------
// Page shell
// --------------------------------------------------------------------------
export const Page: React.FC<{ children: React.ReactNode; className?: string }> = ({ children, className }) => (
  <div className={`lm-page ${className ?? ''}`}>{children}</div>
);

export const PageHead: React.FC<{ title: string; sub?: React.ReactNode }> = ({ title, sub }) => (
  <div className='lm-page__head'>
    <div className='container'>
      <h1>{title}</h1>
      {sub != null && <p className='lm-page__sub'>{sub}</p>}
    </div>
  </div>
);

// --------------------------------------------------------------------------
// Cell group / cell
// --------------------------------------------------------------------------
export const CellGroup: React.FC<{ title?: string; children: React.ReactNode; className?: string }> = ({ title, children, className }) => (
  <div className={`lm-cell-group ${className ?? ''}`}>
    {title && <div className='lm-cell-group__title'>{title}</div>}
    {children}
  </div>
);

interface CellProps {
  title?: React.ReactNode;
  value?: React.ReactNode;
  isLink?: boolean;
  onClick?: () => void;
  children?: React.ReactNode;
  className?: string;
}
export const Cell: React.FC<CellProps> = ({ title, value, isLink, onClick, children, className }) => (
  <div
    className={`lm-cell ${isLink ? 'lm-cell--link' : ''} ${className ?? ''}`}
    onClick={onClick}
    role={onClick ? 'button' : undefined}
    tabIndex={onClick ? 0 : undefined}
  >
    {title != null && <span className='lm-cell__title'>{title}</span>}
    {children != null ? <span className='lm-cell__body'>{children}</span> : null}
    {value != null && <span className='lm-cell__value'>{value}</span>}
    {isLink && <i className='bi bi-chevron-right lm-cell__chevron' />}
  </div>
);

// --------------------------------------------------------------------------
// Goods line-card
// --------------------------------------------------------------------------
interface GoodsLineCardProps {
  picUrl?: string;
  name?: React.ReactNode;
  to?: string;
  specs?: string[];
  price?: number;
  qty?: number;
  /** Replaces the default "× qty" text (e.g. a stepper). */
  qtyControl?: React.ReactNode;
  /** Extra controls on the right (e.g. a remove button). */
  trailing?: React.ReactNode;
}
export const GoodsLineCard: React.FC<GoodsLineCardProps> = ({ picUrl, name, to, specs, price, qty, qtyControl, trailing }) => {
  const title = to ? (
    <Link to={to} className='lm-goods-card__title'>
      {name}
    </Link>
  ) : (
    <span className='lm-goods-card__title'>{name}</span>
  );
  return (
    <div className='lm-goods-card'>
      <img className='lm-goods-card__thumb' src={picUrl || '/content/images/goods_default.png'} alt='' />
      <div className='lm-goods-card__body'>
        {title}
        {specs && specs.length > 0 && (
          <div className='lm-goods-card__specs'>
            {specs.map((s, i) => (
              <span key={`${s}-${i}`} className='lm-spec-chip'>
                {s}
              </span>
            ))}
          </div>
        )}
        <div className='lm-goods-card__footer'>
          {price != null && <span className='lm-goods-card__price'>${price.toFixed(2)}</span>}
          {qtyControl != null ? qtyControl : qty != null && <span className='lm-goods-card__qty'>× {qty}</span>}
          {trailing}
        </div>
      </div>
    </div>
  );
};

// --------------------------------------------------------------------------
// Money summary
// --------------------------------------------------------------------------
export interface SummaryRow {
  label: React.ReactNode;
  value: React.ReactNode;
  /** 'accent' (coral), 'success' (green discount), 'muted', or 'total'. */
  variant?: 'accent' | 'success' | 'muted' | 'total';
}
export const OrderSummary: React.FC<{ rows: SummaryRow[] }> = ({ rows }) => (
  <div className='lm-summary'>
    {rows.map((r, i) => {
      const amtClass =
        r.variant === 'success' ? 'lm-amount lm-amount--success' : r.variant === 'muted' ? 'lm-amount--muted' : 'lm-amount';
      return (
        <div key={i} className={`lm-summary__row ${r.variant === 'total' ? 'lm-summary__row--total' : ''}`}>
          <span>{r.label}</span>
          <span className={r.variant === 'total' ? 'lm-amount' : amtClass}>{r.value}</span>
        </div>
      );
    })}
  </div>
);

// --------------------------------------------------------------------------
// Sticky submit bar
// --------------------------------------------------------------------------
interface SubmitBarProps {
  total?: number;
  label?: React.ReactNode;
  buttonText: React.ReactNode;
  onSubmit: () => void;
  disabled?: boolean;
  loading?: boolean;
  /** Left-side content, e.g. a select-all checkbox. */
  left?: React.ReactNode;
}
export const SubmitBar: React.FC<SubmitBarProps> = ({ total, label = 'Total:', buttonText, onSubmit, disabled, loading, left }) => (
  <div className='lm-submit-bar'>
    {left && <div className='lm-submit-bar__left'>{left}</div>}
    {total != null && (
      <div className='lm-submit-bar__totals'>
        <span className='lm-submit-bar__label'>{label} </span>
        <span className='lm-submit-bar__total'>${total.toFixed(2)}</span>
      </div>
    )}
    <button type='button' className='btn btn-lm-primary lm-submit-bar__action' onClick={onSubmit} disabled={disabled || loading}>
      {loading && <span className='spinner-border spinner-border-sm me-2' role='status' aria-hidden='true' />}
      {buttonText}
    </button>
  </div>
);

// --------------------------------------------------------------------------
// Status tabs
// --------------------------------------------------------------------------
export const StatusTabs: React.FC<{ tabs: string[]; active: number; onChange: (i: number) => void }> = ({ tabs, active, onChange }) => (
  <div className='lm-status-tabs'>
    {tabs.map((t, i) => (
      <button key={t} type='button' className={`lm-status-tabs__tab ${i === active ? 'is-active' : ''}`} onClick={() => onChange(i)}>
        {t}
      </button>
    ))}
  </div>
);

// --------------------------------------------------------------------------
// Result panel (payment status / order confirmation)
// --------------------------------------------------------------------------
interface ResultPanelProps {
  status: 'success' | 'fail' | 'pending';
  title: React.ReactNode;
  sub?: React.ReactNode;
  actions?: React.ReactNode;
}
export const ResultPanel: React.FC<ResultPanelProps> = ({ status, title, sub, actions }) => {
  const icon = status === 'success' ? 'bi-check-circle-fill' : status === 'fail' ? 'bi-x-circle-fill' : 'bi-hourglass-split';
  return (
    <div className={`lm-result lm-result--${status}`}>
      <div className='lm-result__icon'>
        <i className={`bi ${icon}`} />
      </div>
      <h2 className='lm-result__title'>{title}</h2>
      {sub != null && <div className='lm-result__sub'>{sub}</div>}
      {actions != null && <div className='lm-result__actions'>{actions}</div>}
    </div>
  );
};

// --------------------------------------------------------------------------
// Empty state
// --------------------------------------------------------------------------
export const EmptyState: React.FC<{ icon?: string; text: React.ReactNode; children?: React.ReactNode }> = ({ icon = 'bi-inbox', text, children }) => (
  <div className='lm-empty'>
    <div className='lm-empty__icon'>
      <i className={`bi ${icon}`} />
    </div>
    <div className='lm-empty__text'>{text}</div>
    {children}
  </div>
);

// --------------------------------------------------------------------------
// Selectable address card
// --------------------------------------------------------------------------
interface AddressCardProps {
  name?: string;
  tel?: string;
  detail?: string;
  isDefault?: boolean;
  active?: boolean;
  onClick?: () => void;
  trailing?: React.ReactNode;
}
export const AddressCard: React.FC<AddressCardProps> = ({ name, tel, detail, isDefault, active, onClick, trailing }) => (
  <div className={`lm-address-card ${active ? 'is-active' : ''}`} onClick={onClick} role={onClick ? 'button' : undefined} tabIndex={onClick ? 0 : undefined}>
    <div className='lm-address-card__head'>
      <span className='lm-address-card__name'>{name}</span>
      <span className='lm-address-card__tel'>{tel}</span>
      {isDefault && <span className='badge bg-lm-primary ms-1'>Default</span>}
      {trailing && <span className='ms-auto'>{trailing}</span>}
    </div>
    <div className='lm-address-card__detail'>{detail}</div>
  </div>
);
