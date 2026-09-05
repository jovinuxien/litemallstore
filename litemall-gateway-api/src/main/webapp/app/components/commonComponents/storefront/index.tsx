import React from 'react';
import { Link } from 'react-router-dom';

import { money } from 'app/shared/util/money';
import { t } from 'app/i18n';

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
  /**
   * Small line under the specs — Wave-28 uses it for the measured warehouse origin.
   * A ReactNode rather than a string so callers own the icon and wording; absent
   * renders nothing at all, which is the common case (most goods are unmeasured).
   */
  note?: React.ReactNode;
}
export const GoodsLineCard: React.FC<GoodsLineCardProps> = ({ picUrl, name, to, specs, price, qty, qtyControl, trailing, note }) => {
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
        {note != null && <div className='lm-goods-card__note'>{note}</div>}
        <div className='lm-goods-card__footer'>
          {price != null && <span className='lm-goods-card__price'>{money(price)}</span>}
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
  /** 'accent' (coral), 'success' (green discount), 'muted', 'primary' (brand teal), or 'total'. */
  variant?: 'accent' | 'success' | 'muted' | 'primary' | 'total';
}
export const OrderSummary: React.FC<{ rows: SummaryRow[] }> = ({ rows }) => (
  <div className='lm-summary'>
    {rows.map((r, i) => {
      const amtClass =
        r.variant === 'success'
          ? 'lm-amount lm-amount--success'
          : r.variant === 'primary'
            ? 'lm-amount lm-amount--primary'
            : r.variant === 'muted'
              ? 'lm-amount--muted'
              : 'lm-amount';
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
  /** Goes on the bar itself — a display-toggling wrapper would defeat position: sticky. */
  className?: string;
}
export const SubmitBar: React.FC<SubmitBarProps> = ({ total, label, buttonText, onSubmit, disabled, loading, left, className }) => (
  <div className={`lm-submit-bar ${className ?? ''}`}>
    {left && <div className='lm-submit-bar__left'>{left}</div>}
    {total != null && (
      <div className='lm-submit-bar__totals'>
        <span className='lm-submit-bar__label'>{label ?? t('submitBar.total')} </span>
        <span className='lm-submit-bar__total'>{money(total)}</span>
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
// Payment brand badges — Visa / Mastercard / American Express, drawn inline
// (no external asset request; the CSP-friendly same-origin discipline the CJ
// image proxy exists for applies to payment marks too). `muted` greys them out
// for the card-unavailable state so the row stays honest: recognisable, but
// clearly not active.
// --------------------------------------------------------------------------
export const PaymentBrandIcons: React.FC<{ muted?: boolean; className?: string }> = ({ muted, className }) => (
  <span
    className={`lm-paybrands${muted ? ' lm-paybrands--muted' : ''}${className ? ` ${className}` : ''}`}
    aria-label={t('common:kit.acceptedCards')}
  >
    {/* Visa */}
    <svg width='38' height='24' viewBox='0 0 38 24' role='img' aria-label='Visa'>
      <rect width='38' height='24' rx='3' fill='#fff' stroke='#d9dce1' />
      <text x='19' y='16.5' textAnchor='middle' fontFamily='Arial, sans-serif' fontSize='11' fontWeight='bold' fontStyle='italic' fill='#1A1F71'>
        VISA
      </text>
    </svg>
    {/* Mastercard */}
    <svg width='38' height='24' viewBox='0 0 38 24' role='img' aria-label='Mastercard'>
      <rect width='38' height='24' rx='3' fill='#fff' stroke='#d9dce1' />
      <circle cx='15.5' cy='12' r='7' fill='#EB001B' />
      <circle cx='22.5' cy='12' r='7' fill='#F79E1B' />
      <path d='M19 6.5a7 7 0 0 1 0 11 7 7 0 0 1 0-11z' fill='#FF5F00' />
    </svg>
    {/* American Express */}
    <svg width='38' height='24' viewBox='0 0 38 24' role='img' aria-label='American Express'>
      <rect width='38' height='24' rx='3' fill='#2E77BC' />
      <text x='19' y='15.5' textAnchor='middle' fontFamily='Arial, sans-serif' fontSize='9' fontWeight='bold' fill='#fff'>
        AMEX
      </text>
    </svg>
  </span>
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
      {isDefault && <span className='badge bg-lm-primary ms-1'>{t('common:kit.defaultAddress')}</span>}
      {trailing && <span className='ms-auto'>{trailing}</span>}
    </div>
    <div className='lm-address-card__detail'>{detail}</div>
  </div>
);
