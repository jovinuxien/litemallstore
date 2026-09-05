import { readdirSync, readFileSync } from 'fs';
import { join } from 'path';

import { NAMESPACES, SUPPORTED_LANGUAGES } from './index';

/**
 * Every language ships the SAME key set, fully filled. This is the guard the JHipster
 * layout relies on by convention and never enforced: a key added to en/common.json and
 * forgotten in sv/ would silently render English in Swedish — correct-looking, wrong.
 * Here it fails the suite.
 */
const LOCALES_DIR = join(__dirname, 'locales');

type Tree = { [k: string]: string | Tree };

const flatten = (tree: Tree, prefix = ''): Record<string, string> =>
  Object.entries(tree).reduce<Record<string, string>>((acc, [k, v]) => {
    const key = prefix ? `${prefix}.${k}` : k;
    if (v && typeof v === 'object') Object.assign(acc, flatten(v as Tree, key));
    else acc[key] = String(v);
    return acc;
  }, {});

const load = (lang: string, ns: string): Record<string, string> =>
  flatten(JSON.parse(readFileSync(join(LOCALES_DIR, lang, `${ns}.json`), 'utf8')) as Tree);

describe('translation files', () => {
  it('exist for every supported language and namespace, and nothing else', () => {
    for (const lang of SUPPORTED_LANGUAGES) {
      const files = readdirSync(join(LOCALES_DIR, lang)).sort();
      expect(files).toEqual([...NAMESPACES].map(ns => `${ns}.json`).sort());
    }
  });

  it.each([...NAMESPACES])('%s: sv and da carry exactly the English key set', ns => {
    const en = Object.keys(load('en', ns)).sort();
    expect(Object.keys(load('sv', ns)).sort()).toEqual(en);
    expect(Object.keys(load('da', ns)).sort()).toEqual(en);
  });

  it('has no empty strings anywhere', () => {
    for (const lang of SUPPORTED_LANGUAGES) {
      for (const ns of NAMESPACES) {
        Object.entries(load(lang, ns)).forEach(([key, value]) => {
          expect(`${lang}/${ns}:${key}=${value.trim() === '' ? '' : 'ok'}`).toBe(`${lang}/${ns}:${key}=ok`);
        });
      }
    }
  });

  it('keeps every {{placeholder}} of the English string in each translation', () => {
    const placeholders = (s: string) => (s.match(/\{\{\s*\w+\s*\}\}/g) ?? []).map(p => p.replace(/\s/g, '')).sort();
    for (const ns of NAMESPACES) {
      const en = load('en', ns);
      for (const lang of ['sv', 'da']) {
        const other = load(lang, ns);
        Object.keys(en).forEach(key => {
          expect({ key: `${lang}/${ns}:${key}`, placeholders: placeholders(other[key]) }).toEqual({
            key: `${lang}/${ns}:${key}`,
            placeholders: placeholders(en[key]),
          });
        });
      }
    }
  });

  it('keeps every <n>…</n> Trans component tag of the English string in each translation', () => {
    const tags = (s: string) => (s.match(/<\/?\d+>/g) ?? []).sort();
    for (const ns of NAMESPACES) {
      const en = load('en', ns);
      for (const lang of ['sv', 'da']) {
        const other = load(lang, ns);
        Object.keys(en).forEach(key => {
          expect({ key: `${lang}/${ns}:${key}`, tags: tags(other[key]) }).toEqual({ key: `${lang}/${ns}:${key}`, tags: tags(en[key]) });
        });
      }
    }
  });
});
