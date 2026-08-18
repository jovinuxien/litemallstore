import { secureImageUrl } from './imageUrl';

/**
 * Wave 26 — the legacy seed rows (all 49 brands, every seeded topic) point at
 * `http://yanxuan.nosdn.127.net/...`. On the https storefront the browser
 * blocks those, so rendering them produces a broken image, not a picture.
 */
describe('secureImageUrl', () => {
  it('refuses a plain-http image so the caller can draw a placeholder', () => {
    expect(secureImageUrl('http://yanxuan.nosdn.127.net/1541445967645114.png')).toBeNull();
    expect(secureImageUrl('HTTP://yanxuan.nosdn.127.net/x.png')).toBeNull();
  });

  it('passes through what actually loads', () => {
    expect(secureImageUrl('https://cf.cjdropshipping.com/x.jpg')).toBe('https://cf.cjdropshipping.com/x.jpg');
    expect(secureImageUrl('/_cdn/cf/x.jpg')).toBe('/_cdn/cf/x.jpg');
    expect(secureImageUrl('//cdn.example.com/x.jpg')).toBe('//cdn.example.com/x.jpg');
    expect(secureImageUrl('  https://x/y.png  ')).toBe('https://x/y.png');
  });

  it('treats blank and missing as no image', () => {
    expect(secureImageUrl('')).toBeNull();
    expect(secureImageUrl('   ')).toBeNull();
    expect(secureImageUrl(null)).toBeNull();
    expect(secureImageUrl(undefined)).toBeNull();
  });
});
