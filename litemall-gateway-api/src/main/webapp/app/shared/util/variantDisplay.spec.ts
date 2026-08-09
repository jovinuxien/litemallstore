import { analyzeVariantGroup, dimImageMap, SplitDisplay } from './variantDisplay';

/** CJ single-group variant decomposition (Amazon-style Color/Size rows). */
describe('analyzeVariantGroup', () => {
  const sandal = (style: string, size: number) => ({
    value: `Mens Outdoor Casual Beach Sandals Flip-Flops ${style} ${size}`,
  });
  const sandals = ['Black', 'Dark Brown', 'Light Brown'].flatMap(style =>
    [38, 39, 40, 41, 42].map(size => sandal(style, size))
  );

  it('splits prefix-heavy color+size values into Color and Size dims', () => {
    const d = analyzeVariantGroup(sandals) as SplitDisplay;
    expect(d.kind).toBe('split');
    expect(d.dims.map(x => x.name)).toEqual(['Color', 'Size']);
    expect(d.dims[0].values).toEqual(['Black', 'Dark Brown', 'Light Brown']);
    expect(d.dims[1].values).toEqual(['38', '39', '40', '41', '42']);
  });

  it('round-trips picks ⇄ original full value (multi-word styles included)', () => {
    const d = analyzeVariantGroup(sandals) as SplitDisplay;
    const full = d.fullValue({ Color: 'Dark Brown', Size: '39' });
    expect(full).toBe('Mens Outdoor Casual Beach Sandals Flip-Flops Dark Brown 39');
    expect(d.picksOf(full!)).toEqual({ Color: 'Dark Brown', Size: '39' });
    expect(d.fullValue({ Color: 'Dark Brown', Size: '99' })).toBeUndefined();
  });

  it('sorts numeric sizes numerically and labels non-color styles "Style"', () => {
    const d = analyzeVariantGroup([
      { value: 'Widget Alpha 10' },
      { value: 'Widget Alpha 2' },
      { value: 'Widget Bravo 10' },
      { value: 'Widget Bravo 2' },
    ]) as SplitDisplay;
    expect(d.dims[0].name).toBe('Style');
    expect(d.dims[1].values).toEqual(['2', '10']);
  });

  it('handles size-only groups (single Size dim) incl. letter sizes', () => {
    const d = analyzeVariantGroup([
      { value: 'Basic Tee Shirt M' },
      { value: 'Basic Tee Shirt S' },
      { value: 'Basic Tee Shirt XL' },
    ]) as SplitDisplay;
    expect(d.dims).toEqual([{ name: 'Size', values: ['M', 'S', 'XL'] }]);
    expect(d.fullValue({ Size: 'XL' })).toBe('Basic Tee Shirt XL');
  });

  it('falls back to a single Option dim when there is a prefix but no size axis', () => {
    const d = analyzeVariantGroup([
      { value: 'Garden Hose Nozzle Brass Head' },
      { value: 'Garden Hose Nozzle Plastic Head' },
    ]) as SplitDisplay;
    expect(d.kind).toBe('split');
    expect(d.dims[0].name).toBe('Option');
    expect(d.dims[0].values).toEqual(['Brass Head', 'Plastic Head']);
    expect(d.fullValue({ Option: 'Brass Head' })).toBe('Garden Hose Nozzle Brass Head');
    expect(d.fullValue({ Option: 'Steel Head' })).toBeUndefined();
  });

  it('maps dim values to distinct SKU images, skipping the main photo (dimImageMap)', () => {
    const d = analyzeVariantGroup(sandals) as SplitDisplay;
    const skus = [
      { full: 'Mens Outdoor Casual Beach Sandals Flip-Flops Black 38', url: '/black.jpg' },
      { full: 'Mens Outdoor Casual Beach Sandals Flip-Flops Black 39', url: '/black-39.jpg' }, // first per style wins
      { full: 'Mens Outdoor Casual Beach Sandals Flip-Flops Dark Brown 38', url: '/main.jpg' }, // = main pic → skipped
      { full: 'Mens Outdoor Casual Beach Sandals Flip-Flops Light Brown 40', url: '/light.jpg' },
      { full: 'not a variant value', url: '/junk.jpg' },
      { full: 'Mens Outdoor Casual Beach Sandals Flip-Flops Light Brown 41' }, // no url
    ];
    expect(dimImageMap(skus, d, 'Color', '/main.jpg')).toEqual({ Black: '/black.jpg', 'Light Brown': '/light.jpg' });
    // today's catalog: every SKU carries the main photo → empty map, tiles stay text-only
    expect(dimImageMap(skus.map(s => ({ ...s, url: '/main.jpg' })), d, 'Color', '/main.jpg')).toEqual({});
  });

  it('stays plain for short unshared values, images, and lossy decompositions', () => {
    expect(analyzeVariantGroup([{ value: 'Red' }, { value: 'Blue' }]).kind).toBe('plain');
    expect(analyzeVariantGroup([{ value: 'A 38', picUrl: '/x.jpg' }, { value: 'A 39' }]).kind).toBe('plain');
    expect(analyzeVariantGroup([{ value: 'Shoe  Black 38' }, { value: 'Shoe Black  38' }]).kind).toBe('plain'); // duplicate combo
    expect(analyzeVariantGroup([{ value: 'X' }]).kind).toBe('plain');
  });
});
