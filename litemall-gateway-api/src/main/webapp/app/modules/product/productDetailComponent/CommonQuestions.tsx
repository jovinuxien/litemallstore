import React, { useState } from 'react';
import { Link } from 'react-router-dom';

import { FAQ_SECTIONS, FaqEntry } from 'app/modules/static/faqData';
import { useTranslation } from 'app/i18n';

/**
 * "Common questions" accordion on the PDP (the Amazon Q&A slot). Content is a
 * curated subset of the shared Wave-9.1 FAQ (`faqData.ts` — the same honesty-
 * ruled copy /help and /service render, so the PDP can never drift from the
 * help center). Deliberately NOT `/srv/issue`: its seed rows state a flat-fee
 * shipping policy that contradicts the live CJ freight chooser.
 */
export const PDP_FAQ_IDS = ['delivery-time', 'track-order', 'return-window', 'payment-methods'];

export const pdpFaqEntries = (): FaqEntry[] => {
  const byId = new Map<string, FaqEntry>();
  FAQ_SECTIONS.forEach(s => s.entries.forEach(e => byId.set(e.id, e)));
  return PDP_FAQ_IDS.map(id => byId.get(id)).filter((e): e is FaqEntry => !!e);
};

const CommonQuestions: React.FC = () => {
  const { t } = useTranslation('product');
  const [open, setOpen] = useState<string | null>(null);
  const entries = pdpFaqEntries();
  if (!entries.length) return null;

  return (
    <section className='lm-pdp__faq'>
      <h3 className='lm-pdp__faqtitle'>{t('faq.title')}</h3>
      <ul className='lm-pdp__faqlist'>
        {entries.map(e => (
          <li key={e.id} className={open === e.id ? 'is-open' : ''}>
            <button type='button' onClick={() => setOpen(o => (o === e.id ? null : e.id))} aria-expanded={open === e.id}>
              <i className={`bi ${open === e.id ? 'bi-dash-lg' : 'bi-plus-lg'}`} />
              {e.q}
            </button>
            {open === e.id && (
              <div className='lm-pdp__faqbody'>
                <p>{e.a}</p>
                <Link to={`/help#${e.id}`} className='lm-pdp__faqmore'>
                  {t('faq.more')}
                </Link>
              </div>
            )}
          </li>
        ))}
      </ul>
      <Link to='/help' className='lm-pdp__faqall'>
        {t('faq.all')} <i className='bi bi-chevron-right' style={{ fontSize: '0.7em' }} />
      </Link>
    </section>
  );
};

export default CommonQuestions;
