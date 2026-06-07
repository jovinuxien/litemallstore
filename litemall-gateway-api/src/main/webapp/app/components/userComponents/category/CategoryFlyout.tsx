import React, { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { getCurrentCatalogData } from 'app/modules/Category/categorySlice';
import { CategoryData } from 'app/shared/model/category/category.models';
import './category-flyout.scss';

// catalog/index items carry the id/name either flat or under categoryId/categoryName.
type IndexCategory = CategoryData & { categoryId?: { id?: number }; categoryName?: string };

const catId = (c: IndexCategory): number | undefined => c.id ?? c.categoryId?.id;
const catName = (c: IndexCategory): string | undefined => c.name ?? c.categoryName;

interface Props {
  categories: IndexCategory[];
  limit?: number;
}

/**
 * Home level-1 category menu with an Amazon-style hover flyout. Hovering (or
 * focusing) a top category opens a side panel listing its level-2 children,
 * lazy-loaded once via getSecondCategories and cached in the category slice.
 * Clicking a child → /search?category=<childId>; clicking the level-1 label →
 * /search?category=<level1Id>. Both land on the themed faceted Search page.
 */
const CategoryFlyout: React.FC<Props> = ({ categories, limit = 12 }) => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const [activeId, setActiveId] = useState<number | null>(null);
  const closeTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const children = useAppSelector(state => (activeId != null ? state.category.data.secondCategoriesById[activeId] : undefined));

  const open = (id?: number) => {
    if (id == null) return;
    if (closeTimer.current) clearTimeout(closeTimer.current);
    setActiveId(id);
    // Lazy-load this category's level-2 children (via /catalog/current) once.
    if (!children || activeId !== id) dispatch(getCurrentCatalogData(id));
  };

  const scheduleClose = () => {
    if (closeTimer.current) clearTimeout(closeTimer.current);
    closeTimer.current = setTimeout(() => setActiveId(null), 160);
  };

  const goCategory = (id?: number) => {
    if (id == null) return;
    setActiveId(null);
    navigate(`/search?category=${id}`);
  };

  const shown = categories.slice(0, limit);
  const activeChildren = children ?? [];

  return (
    <aside className="lm-catmenu" onMouseLeave={scheduleClose}>
      <ul className="lm-catmenu__list">
        {shown.map(category => {
          const id = catId(category);
          return (
            <li
              key={id}
              className={`lm-catmenu__item${activeId === id ? ' is-active' : ''}`}
              onMouseEnter={() => open(id)}
              onFocus={() => open(id)}
            >
              <button type="button" className="lm-catmenu__label" onClick={() => goCategory(id)}>
                <span>{catName(category)}</span>
                <span className="lm-catmenu__arrow">›</span>
              </button>
            </li>
          );
        })}
      </ul>

      {activeId != null && (
        <div className="lm-catmenu__flyout" onMouseEnter={() => open(activeId)} onMouseLeave={scheduleClose}>
          <div className="lm-catmenu__flyout-head">
            <button type="button" className="lm-catmenu__flyout-all" onClick={() => goCategory(activeId)}>
              Shop all {catName(shown.find(c => catId(c) === activeId) ?? ({} as IndexCategory)) ?? 'category'} ›
            </button>
          </div>
          {activeChildren.length > 0 ? (
            <ul className="lm-catmenu__sublist">
              {activeChildren.map(sc => (
                <li key={sc.id}>
                  <button type="button" onClick={() => goCategory(sc.id)}>
                    {sc.name}
                  </button>
                </li>
              ))}
            </ul>
          ) : (
            <p className="lm-catmenu__loading">Loading subcategories…</p>
          )}
        </div>
      )}
    </aside>
  );
};

export default CategoryFlyout;
