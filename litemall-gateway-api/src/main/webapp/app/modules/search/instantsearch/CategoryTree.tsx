import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import { BASE_URL_CONTEXT } from 'app/config/api';
import { baseAxios } from 'app/config/axiosinstance';
import { resetPageTitle, setPageTitle } from 'app/shared/util/pageTitle';

type Node = { id: number; name: string; level?: string; count?: number };

interface CategoryDetail {
  category?: Node;
  breadcrumb?: Node[];
  subcategories?: Node[];
}

/**
 * Category-scoped left-rail navigation shown on `/category/:id`.
 *
 * The flat `category_ids` RefinementList can't show a tree here: once a category
 * filter is active OCS collapses the `category_ids` facet to the selected id
 * alone, so no children ever appear. This instead renders the real subcategory
 * TREE (with per-child product counts) + breadcrumb, sourced from
 * goods-management's `GET /srv/search/category/{id}` (which derives the child
 * counts from the `category_names` facet). Clicking a node drills into
 * `/category/{childId}`; the Search page re-seeds its scope on that navigation.
 */
const CategoryTree: React.FC<{ categoryId: string }> = ({ categoryId }) => {
  const [detail, setDetail] = useState<CategoryDetail | null>(null);

  useEffect(() => {
    let cancelled = false;
    // size=1: we only need category/breadcrumb/subcategories here, not the hits.
    baseAxios
      .get(`${BASE_URL_CONTEXT}/search/category/${categoryId}?size=1`)
      .then(res => {
        const d = (res.data?.data ?? res.data ?? {}) as CategoryDetail;
        if (!cancelled) setDetail(d);
      })
      .catch(() => {
        if (!cancelled) setDetail(null);
      });
    return () => {
      cancelled = true;
    };
  }, [categoryId]);

  // Tab title follows the category on client-side navigation (a full page
  // load arrives with the edge-injected title, Wave-13). This component owns
  // the fetched category payload, so the title lives here rather than in
  // Search.tsx, which never sees the category name.
  useEffect(() => {
    const name = detail?.category?.name ?? detail?.breadcrumb?.at(-1)?.name;
    if (name) setPageTitle(name);
    return resetPageTitle;
  }, [detail]);

  if (!detail?.category) return null;

  const crumbs = detail.breadcrumb ?? [];
  // Wave 26: a child with a measured count of zero is a link into an empty page
  // — the narrowed catalogue leaves several of them under the anchor roots. A
  // child whose count the backend did not measure is kept (absent ≠ empty).
  const subs = (detail.subcategories ?? []).filter(s => typeof s.count !== 'number' || s.count > 0);

  return (
    <section className="lm-isearch__facet lm-cattree">
      <h3>Department</h3>

      {crumbs.length > 0 && (
        <nav className="lm-cattree__crumbs" aria-label="Category breadcrumb">
          {crumbs.map((c, i) => (
            <React.Fragment key={c.id}>
              {i > 0 && <span className="lm-cattree__sep">›</span>}
              {i < crumbs.length - 1 ? (
                <Link to={`/category/${c.id}`}>{c.name}</Link>
              ) : (
                <span className="lm-cattree__current">{c.name}</span>
              )}
            </React.Fragment>
          ))}
        </nav>
      )}

      {subs.length > 0 ? (
        <ul className="lm-cattree__list">
          {subs.map(s => (
            <li key={s.id}>
              <Link to={`/category/${s.id}`}>
                <span className="lm-cattree__name">{s.name}</span>
                {typeof s.count === 'number' && <span className="lm-cattree__count">{s.count}</span>}
              </Link>
            </li>
          ))}
        </ul>
      ) : (
        <p className="lm-cattree__leaf">No subcategories</p>
      )}
    </section>
  );
};

export default CategoryTree;
