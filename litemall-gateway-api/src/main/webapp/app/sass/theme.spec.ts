import { readdirSync, readFileSync, statSync } from 'fs';
import { join } from 'path';

/**
 * Theme hierarchy + document type scale (2026-09-05). These are source-level pins
 * for what the headless computed-style check proves in a real browser:
 *  - the typeface has ONE home (`--lm-font` in global.scss) — four stylesheets used
 *    to override it with Roboto/Helvetica and diverged per platform;
 *  - Bootstrap `primary`, links and the two primary button variants are remapped to
 *    the teal tokens, so an unclassed <Link> can no longer render #0d6efd;
 *  - every document page wears `.lm-doc` and its body paragraphs are no longer
 *    14px muted grey — `small text-muted` stays only on "Last updated" captions.
 */
const APP = join(__dirname, '..');

const walk = (dir: string, out: string[] = []): string[] => {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) walk(p, out);
    else out.push(p);
  }
  return out;
};

const DOC_PAGES = ['Help', 'Returns', 'Terms', 'Delivery', 'Payments', 'Privacy', 'Cookies', 'CustomerService', 'NotFound'];

describe('typeface', () => {
  it('is declared only in global.scss', () => {
    const offenders = walk(APP)
      .filter(p => /\.(scss|css)$/.test(p) && !p.endsWith(join('sass', 'global.scss')))
      .filter(p => /font-family\s*:/.test(readFileSync(p, 'utf8')));
    expect(offenders).toEqual([]);
  });
});

describe('Bootstrap primary remap', () => {
  const global = readFileSync(join(APP, 'sass', 'global.scss'), 'utf8');

  it('points primary, links and both primary button variants at the teal tokens', () => {
    expect(global).toMatch(/--bs-primary:\s*var\(--lm-primary\)/);
    expect(global).toMatch(/--bs-primary-rgb:\s*14,\s*124,\s*134/);
    expect(global).toMatch(/--bs-link-color-rgb:\s*14,\s*124,\s*134/);
    expect(global).toMatch(/--bs-link-hover-color-rgb:\s*10,\s*93,\s*101/);
    expect(global).toMatch(/\.btn-primary\s*\{[^}]*--bs-btn-bg:\s*var\(--lm-primary\)/);
    expect(global).toMatch(/\.btn-outline-primary\s*\{[^}]*--bs-btn-color:\s*var\(--lm-primary\)/);
  });

  it('defines the --lm-* palette on :root in global.scss, not only beside the product card', () => {
    expect(global).toMatch(/--lm-primary:\s*#0e7c86/);
    expect(global).toMatch(/--lm-text:\s*#1f2a2e/);
    const card = readFileSync(join(APP, 'components', 'userComponents', 'card', 'product-card.scss'), 'utf8');
    expect(card).not.toMatch(/--lm-primary:\s*#/);
  });

  it('gives anchors the quiet-at-rest, underline-on-hover treatment', () => {
    expect(global).toMatch(/^a\s*\{\s*\n\s*text-decoration:\s*none;/m);
  });
});

describe('document pages', () => {
  it.each(DOC_PAGES)('%s wears .lm-doc and demotes no heading with h4/h6', page => {
    const src = readFileSync(join(APP, 'modules', 'static', `${page}.tsx`), 'utf8');
    expect(src).toMatch(/className='container my-[45] lm-doc'/);
    expect(src).not.toMatch(/<h[12] className='h[46]/);
  });

  it.each(DOC_PAGES)('%s keeps small+muted only for the "Last updated" caption and the help footnote', page => {
    const src = readFileSync(join(APP, 'modules', 'static', `${page}.tsx`), 'utf8');
    const muted = [...src.matchAll(/<p className='[^']*text-muted[^']*'>\s*([^<]{0,20})/g)].map(m => m[1].trim());
    muted.forEach(lead => expect({ page, lead, caption: /^(Last updated|Not solved\?)/.test(lead) }).toEqual({ page, lead, caption: true }));
  });
});
