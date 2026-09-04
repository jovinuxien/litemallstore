import React from 'react';
import { NavDropdown } from 'react-bootstrap';

import { LANGUAGE_NAMES, type Lang, useTranslation } from 'app/i18n';
import { setLocale, useLocale } from 'app/i18n/locale';

/**
 * Language switcher (i18n foundation). Renders NOTHING while only one language is
 * enabled — the JHipster reference's `LocaleMenu` rule, and the storefront's own
 * absent-means-hidden convention: until `LITEMALL_I18N_LANGUAGES` lists sv/da there is
 * no menu to find. Names are endonyms, never flags (a flag names a country, not a
 * language — Swedish is spoken in Finland, and Danish shoppers do not want a Dannebrog
 * next to "English").
 *
 * `variant='header'` is a NavDropdown in the account cluster; `variant='footer'` is a
 * plain row of buttons for the legal bar.
 */
const LanguageSwitcher: React.FC<{ variant?: 'header' | 'footer' }> = ({ variant = 'header' }) => {
  const { lang, enabled } = useLocale();
  const { t } = useTranslation();
  if (enabled.length < 2) return null;

  const choose = (next: Lang) => {
    if (next !== lang) void setLocale(next);
  };

  if (variant === 'footer') {
    return (
      <div className='d-flex align-items-center gap-2 lm-lang lm-lang--footer' role='group' aria-label={t('language.label')}>
        <i className='bi bi-globe2' aria-hidden='true' />
        {enabled.map(code => (
          <button
            key={code}
            type='button'
            lang={code}
            className={`btn btn-link btn-sm p-0 text-decoration-none ${code === lang ? 'link-light fw-semibold' : 'link-secondary'}`}
            aria-pressed={code === lang}
            onClick={() => choose(code)}
          >
            {LANGUAGE_NAMES[code]}
          </button>
        ))}
      </div>
    );
  }

  return (
    <NavDropdown
      align='end'
      className='lm-header__acct lm-lang lm-lang--header'
      id='language-menu'
      title={
        <span className='lm-header__stack'>
          <small>
            <i className='bi bi-globe2 me-1' aria-hidden='true' />
            {t('language.label')}
          </small>
          <strong lang={lang}>{LANGUAGE_NAMES[lang]}</strong>
        </span>
      }
    >
      {enabled.map(code => (
        <NavDropdown.Item key={code} lang={code} active={code === lang} onClick={() => choose(code)}>
          {LANGUAGE_NAMES[code]}
        </NavDropdown.Item>
      ))}
    </NavDropdown>
  );
};

export default LanguageSwitcher;
