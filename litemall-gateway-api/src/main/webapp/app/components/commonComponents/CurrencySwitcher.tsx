import React from 'react';
import { NavDropdown } from 'react-bootstrap';
import { useLocation } from 'react-router-dom';

import { useTranslation } from 'app/i18n';
import { currentLocaleTag, useLocale } from 'app/i18n/locale';
import { type Currency, setCurrency, useCurrency } from 'app/shared/util/currency';

/**
 * Display-currency selector (docs/spec-display-currency.md). Renders NOTHING while
 * only EUR is on offer — the storefront's absent-means-hidden rule: until
 * `LITEMALL_CURRENCY_DISPLAY` lists more and the edge holds fresh ECB rates, there
 * is no menu to find.
 *
 * It is also absent on the checkout and pay pages. Switching re-renders the page
 * (prices are written by a plain formatter, see App.tsx), which would wipe a
 * half-filled checkout; and the amount being paid there is the euro one regardless.
 *
 * The choice changes how prices are WRITTEN, never what is charged — the menu says
 * so whenever a non-euro currency is active.
 */
const HIDDEN_ON = /^\/(checkout|pay)(\/|$)/;

/** "Swedish krona" / "svensk krona" — the browser's own name for the currency. */
export const currencyName = (code: Currency, tag: string): string => {
  try {
    return new Intl.DisplayNames([tag], { type: 'currency' }).of(code) ?? code;
  } catch {
    return code;
  }
};

const CurrencySwitcher: React.FC<{ variant?: 'header' | 'footer' }> = ({ variant = 'header' }) => {
  const { currency, enabled } = useCurrency();
  useLocale(); // currency names follow the language
  const { t } = useTranslation();
  const { pathname } = useLocation();
  if (enabled.length < 2 || HIDDEN_ON.test(pathname)) return null;

  const tag = currentLocaleTag();
  const choose = (next: Currency) => {
    if (next !== currency) setCurrency(next);
  };

  if (variant === 'footer') {
    return (
      <div className='d-flex align-items-center gap-2 lm-cur lm-cur--footer' role='group' aria-label={t('currency.label')}>
        <i className='bi bi-cash-coin' aria-hidden='true' />
        {enabled.map(code => (
          <button
            key={code}
            type='button'
            title={currencyName(code, tag)}
            className={`btn btn-link btn-sm p-0 text-decoration-none ${code === currency ? 'link-light fw-semibold' : 'link-secondary'}`}
            aria-pressed={code === currency}
            onClick={() => choose(code)}
          >
            {code}
          </button>
        ))}
      </div>
    );
  }

  return (
    <NavDropdown
      align='end'
      className='lm-header__acct lm-cur lm-cur--header'
      id='currency-menu'
      title={
        <span className='lm-header__stack'>
          <small>{t('currency.label')}</small>
          <strong>{currency}</strong>
        </span>
      }
    >
      {enabled.map(code => (
        <NavDropdown.Item key={code} active={code === currency} onClick={() => choose(code)}>
          <strong>{code}</strong> <span className='small'>{currencyName(code, tag)}</span>
        </NavDropdown.Item>
      ))}
      {currency !== 'EUR' && (
        <>
          <NavDropdown.Divider />
          <NavDropdown.ItemText className='small text-muted lm-cur__note'>{t('currency.note', { currency })}</NavDropdown.ItemText>
        </>
      )}
    </NavDropdown>
  );
};

export default CurrencySwitcher;
