import React, { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { useAppDispatch } from 'app/config/store';
import { getCurrentCatalogData } from 'app/modules/Category/categorySlice';
import { CategoryData } from 'app/shared/model/category/category.models';
import './category-flyout.scss';

// catalog items carry id/name either flat or under categoryId/categoryName.
type IndexCategory = CategoryData & { categoryId?: { id?: number }; categoryName?: string };

const catId = (c: IndexCategory): number | undefined => c?.id ?? c?.categoryId?.id;
const catName = (c: IndexCategory): string | undefined => c?.name ?? c?.categoryName;

interface Props {
  categories: IndexCategory[];
  limit?: number;
}

/**
 * Home level-1 category menu with an Amazon-style flyout that is also
 * touch-friendly. Desktop: hovering a top category opens a side panel of its
 * level-2 children. Mobile: the arrow toggles the children inline. Children are
 * fetched once via /catalog/current (getsecondcategory 404s) and cached locally.
 * Clicking a child → /search?category_ids=<childId>; clicking the level-1 label
 * (or "Shop all") → /search?category_ids=<level1Id>. Both land on the Search page.
 */
const CategoryFlyout: React.FC<Props> = ({ categories, limit = 12 }) => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const [activeId, setActiveId] = useState<number | null>(null);
  const [cache, setCache] = useState<Record<number, IndexCategory[]>>({});
  const closeTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const open = async (id?: number) => {
    if (id == null) return;
    if (closeTimer.current) clearTimeout(closeTimer.current);
    setActiveId(id);
    if (cache[id]) return;
    const res = await dispatch(getCurrentCatalogData(id));
    if (getCurrentCatalogData.fulfilled.match(res)) {
      setCache(prev => ({ ...prev, [id]: (res.payload.currentSubCategory ?? []) as IndexCategory[] }));
    }
  };

  const toggle = (id?: number) => {
    if (id == null) return;
    if (activeId === id) setActiveId(null);
    else open(id);
  };

  const scheduleClose = () => {
    if (closeTimer.current) clearTimeout(closeTimer.current);
    closeTimer.current = setTimeout(() => setActiveId(null), 160);
  };

  const goCategory = (id?: number) => {
    if (id == null) return;
    setActiveId(null);
    navigate(`/search?category_ids=${id}`);
  };

  const shown = categories.slice(0, limit);

  return (
    <aside className="lm-catmenu" onMouseLeave={scheduleClose}>
      <div className="lm-catmenu__title">Shop by category</div>
      <ul className="lm-catmenu__list">
        {shown.map(category => {
          const id = catId(category);
          const isActive = activeId === id;
          const children = (id != null ? cache[id] : undefined) ?? [];
          return (
            <li key={id} className={`lm-catmenu__item${isActive ? ' is-active' : ''}`} onMouseEnter={() => open(id)}>
              <div className="lm-catmenu__row">
                <button type="button" className="lm-catmenu__label" onClick={() => goCategory(id)}>
                  {catName(category)}
                </button>
                <button
                  type="button"
                  className="lm-catmenu__toggle"
                  aria-expanded={isActive}
                  aria-label={`Show ${catName(category) ?? 'category'} subcategories`}
                  onClick={() => toggle(id)}
                >
                  ›
                </button>
              </div>

              {isActive && (
                <div className="lm-catmenu__flyout" onMouseEnter={() => open(id)} onMouseLeave={scheduleClose}>
                  <div className="lm-catmenu__flyout-head">
                    <button type="button" className="lm-catmenu__flyout-all" onClick={() => goCategory(id)}>
                      Shop all {catName(category)} ›
                    </button>
                  </div>
                  {children.length > 0 ? (
                    <ul className="lm-catmenu__sublist">
                      {children.map(sc => {
                        const sid = catId(sc);
                        return (
                          <li key={sid}>
                            <button type="button" onClick={() => goCategory(sid)}>
                              {catName(sc)}
                            </button>
                          </li>
                        );
                      })}
                    </ul>
                  ) : (
                    <p className="lm-catmenu__loading">Loading subcategories…</p>
                  )}
                </div>
              )}
            </li>
          );
        })}
      </ul>
    </aside>
  );
};

export default CategoryFlyout;
