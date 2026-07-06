import React, { useState } from 'react';
import { Link } from 'react-router-dom';

import { useAppSelector } from 'app/config/store';

// Categories serialize as DDD aggregates ({ categoryId: { id }, categoryName, iconUrl }) or the
// flat { id, name, picUrl } shape depending on the endpoint — read both defensively (same rule
// as Home.tsx / Search.tsx).
const catId = (c: any): number | undefined => c?.id ?? c?.categoryId?.id;
const catName = (c: any): string | undefined => c?.name ?? c?.categoryName;

/**
 * Browse tree for the /search rail: top L1 categories (the catalog arrives most-promising-first —
 * the backend orders /srv/catalog/all by on-sale goods count), each toggling open to its L2
 * subcategories. Links navigate to /category/:id, which swaps this tree for the scoped
 * CategoryTree (breadcrumb + per-subcategory counts).
 */
const CatalogTreeNav: React.FC<{ limit?: number }> = ({ limit = 10 }) => {
  const { dataCategoryIndex, dataCatalogAll } = useAppSelector(state => state.category.data);
  const [open, setOpen] = useState<Record<string, boolean>>({});

  const roots = (dataCatalogAll?.categoryList?.length ? dataCatalogAll.categoryList : dataCategoryIndex?.categoryList) ?? [];
  const subTree: Record<string, any[]> = (dataCatalogAll?.allList as any) ?? {};
  if (!roots.length) {
    return null;
  }

  return (
    <section className="lm-isearch__facet lm-cattree" data-testid="catalog-tree-nav">
      <h3>Categories</h3>
      <ul className="lm-cattree__list">
        {roots.slice(0, limit).map(root => {
          const id = catId(root);
          const key = String(id);
          const subs = (subTree[key] ?? []) as any[];
          const isOpen = !!open[key];
          return (
            <li key={key} className="lm-cattree__node">
              <div className="lm-cattree__row">
                <Link to={`/category/${id}`} className="lm-cattree__link">
                  {catName(root)}
                </Link>
                {subs.length > 0 && (
                  <button
                    type="button"
                    className="lm-cattree__toggle"
                    aria-expanded={isOpen}
                    aria-label={`${isOpen ? 'Collapse' : 'Expand'} ${catName(root)}`}
                    onClick={() => setOpen(prev => ({ ...prev, [key]: !prev[key] }))}
                  >
                    {isOpen ? '▾' : '▸'}
                  </button>
                )}
              </div>
              {isOpen && subs.length > 0 && (
                <ul className="lm-cattree__sublist">
                  {subs.map(sub => (
                    <li key={catId(sub)}>
                      <Link to={`/category/${catId(sub)}`} className="lm-cattree__link lm-cattree__link--sub">
                        {catName(sub)}
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </li>
          );
        })}
      </ul>
    </section>
  );
};

export default CatalogTreeNav;
