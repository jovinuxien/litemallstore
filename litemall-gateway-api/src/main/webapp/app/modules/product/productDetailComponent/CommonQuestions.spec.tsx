import React from 'react';
import { fireEvent, render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

import CommonQuestions, { PDP_FAQ_IDS, pdpFaqEntries } from './CommonQuestions';

/**
 * PDP "Common questions": a curated subset of the shared /help FAQ content
 * (never /srv/issue — its seed copy contradicts the live freight policy).
 */
describe('CommonQuestions', () => {
  it('curates existing FAQ entries in the declared order', () => {
    const entries = pdpFaqEntries();
    expect(entries.map(e => e.id)).toEqual(PDP_FAQ_IDS);
    entries.forEach(e => {
      expect(e.q).toBeTruthy();
      expect(e.a).toBeTruthy();
    });
  });

  it('renders collapsed questions and expands one with its help-center deep link', () => {
    const { container, getByText } = render(
      <MemoryRouter>
        <CommonQuestions />
      </MemoryRouter>
    );
    expect(container.querySelectorAll('.lm-pdp__faqlist li').length).toBe(PDP_FAQ_IDS.length);
    expect(container.querySelector('.lm-pdp__faqbody')).toBeNull();

    fireEvent.click(getByText('How long does delivery take?'));
    const body = container.querySelector('.lm-pdp__faqbody');
    expect(body).not.toBeNull();
    expect(body!.querySelector('a')!.getAttribute('href')).toBe('/help#delivery-time');
  });

  it('links the help center hub', () => {
    const { getByText } = render(
      <MemoryRouter>
        <CommonQuestions />
      </MemoryRouter>
    );
    expect(getByText(/Visit the help center/).getAttribute('href')).toBe('/help');
  });
});
