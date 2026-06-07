import React, { useEffect, useMemo, useRef, useState } from 'react';

import { IGood } from 'app/shared/model/product/product.model';
import ProductCard from './ProductCard';

interface Props {
  items: IGood[];
  /** how many cards to reveal initially */
  initial?: number;
  /** how many more to reveal each time the sentinel scrolls into view */
  step?: number;
  /** prefix for React keys so multiple grids on one page stay unique */
  keyPrefix?: string;
}

/**
 * Self-contained infinite-scroll product grid. Each instance owns its reveal
 * counter and IntersectionObserver sentinel, so several grids can coexist on a
 * page and load independently (no shared window scroll listener). Progressively
 * reveals items already loaded into `items`.
 */
const InfiniteProductGrid: React.FC<Props> = ({ items, initial = 10, step = 10, keyPrefix = 'item' }) => {
  const [visible, setVisible] = useState(initial);
  const sentinelRef = useRef<HTMLDivElement | null>(null);

  const total = items?.length ?? 0;
  const hasMore = visible < total;
  const shown = useMemo(() => (items ?? []).slice(0, visible), [items, visible]);

  // Reset the reveal window when the source list changes (e.g. a new fetch).
  useEffect(() => {
    setVisible(initial);
  }, [total, initial]);

  useEffect(() => {
    if (!hasMore) return undefined;
    const node = sentinelRef.current;
    if (!node) return undefined;

    const observer = new IntersectionObserver(
      entries => {
        if (entries.some(e => e.isIntersecting)) {
          setVisible(prev => Math.min(prev + step, total));
        }
      },
      { rootMargin: '300px' }
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, [hasMore, step, total]);

  return (
    <>
      <div className="lm-grid">
        {shown.map(product => (
          <ProductCard key={`${keyPrefix}-${product.id}`} product={product} />
        ))}
      </div>
      {hasMore && (
        <div ref={sentinelRef} className="lm-grid__sentinel">
          Loading more products…
        </div>
      )}
    </>
  );
};

export default InfiniteProductGrid;
