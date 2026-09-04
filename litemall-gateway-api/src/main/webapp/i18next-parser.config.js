/**
 * Key-sync check for the storefront translations (`npm run i18n:check`).
 *
 * The JSON under app/i18n/locales is the SOURCE OF TRUTH for every language, English
 * included — code carries keys, not inline English. So the parser is run in check mode
 * only: `--fail-on-update` fails when a `t('ns:key')` in code has no entry in ANY of the
 * three languages, or when a JSON key is no longer referenced. `npm run i18n:extract`
 * writes the missing keys (empty) so you can fill them in.
 *
 * Keys built at runtime (`errors:byCode.<errno>`, `footer.promises.<key>.title`) cannot
 * be seen statically, so unused-key pruning is off; the jest parity test owns the
 * cross-language structure check.
 */
module.exports = {
  locales: ['en', 'sv', 'da'],
  input: ['app/**/*.{ts,tsx}', '!app/**/*.spec.{ts,tsx}', '!app/i18n/**'],
  output: 'app/i18n/locales/$LOCALE/$NAMESPACE.json',
  defaultNamespace: 'common',
  namespaceSeparator: ':',
  keySeparator: '.',
  pluralSeparator: '_',
  defaultValue: '',
  // The JSON is hand-ordered by feature, not alphabetically; a re-sort is not drift.
  sort: false,
  // Unused keys are NOT pruned here (runtime-built keys such as errors:byCode.<errno>
  // are invisible to a static scan); the jest parity test owns structure. The check
  // therefore fails on exactly one thing: a key used in code that some language lacks.
  keepRemoved: true,
  createOldCatalogs: false,
  indentation: 2,
  lexers: {
    ts: ['JavascriptLexer'],
    tsx: ['JsxLexer'],
    default: ['JavascriptLexer'],
  },
};
