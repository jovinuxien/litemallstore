/**
 * Display helper for the order's shipping-address snapshot. New orders (V48)
 * store the structured "detail, city, region zip, Country" form — rendered as
 * stacked lines; legacy rows are an unseparated concat and stay a single line.
 */
export const orderAddressLines = (address?: string): string[] => {
  const a = (address ?? '').trim();
  if (!a || a.startsWith('PICKUP: ')) {
    return a ? [a] : [];
  }
  return a.includes(', ')
    ? a.split(', ').map(s => s.trim()).filter(Boolean)
    : [a];
};
