import { __resetLocale, applyEnabledLanguages, setLocale } from './locale';
import { describeError } from './errors';

jest.mock('app/shared/config/siteConfig', () => ({ loadSiteConfig: jest.fn().mockResolvedValue({ i18nLanguages: ['en'] }) }));

/**
 * Server errors are localised BY ERRNO on the client; an errno we have not catalogued
 * keeps the server's text verbatim — the property every typed-refusal acceptance test
 * in this repo relies on.
 */
beforeEach(async () => {
  await __resetLocale();
});

it('translates a catalogued errno regardless of the server wording', async () => {
  expect(describeError(704, 'username already exists')).toBe('This username is already registered.');
  await applyEnabledLanguages(['sv']);
  await setLocale('sv');
  expect(describeError(704, 'username already exists')).toBe('Användarnamnet är redan registrerat.');
  expect(describeError('704', '')).toBe('Användarnamnet är redan registrerat.');
});

it('renders an uncatalogued errno verbatim', () => {
  expect(describeError(653, 'this group has expired — start a new one or buy at regular price')).toBe(
    'this group has expired — start a new one or buy at regular price'
  );
  expect(describeError(-1, 'mistake')).toBe('mistake');
});

it('never returns an empty string', () => {
  expect(describeError(999, '')).toBe('Request failed');
  expect(describeError(undefined, null)).toBe('Request failed');
  expect(describeError(999, '   ')).toBe('Request failed');
});
