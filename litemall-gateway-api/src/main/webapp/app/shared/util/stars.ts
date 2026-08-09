/** Full/half/empty star icon classes for a 0–5 rating, half-rounded — shared
 * by the PDP rating row and the grid ProductCard so star rendering never
 * drifts between surfaces. */
export const starIcons = (rating: number): string[] => {
  const r = Math.round(Math.max(0, Math.min(5, rating)) * 2) / 2;
  return [1, 2, 3, 4, 5].map(i => (i <= r ? 'bi-star-fill' : i - 0.5 === r ? 'bi-star-half' : 'bi-star'));
};
