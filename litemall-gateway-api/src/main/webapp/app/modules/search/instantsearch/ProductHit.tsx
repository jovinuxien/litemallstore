import React from 'react';

import ProductCard from 'app/components/userComponents/card/ProductCard';
import { IGood } from 'app/shared/model/product/product.model';

import renderSnippet from './highlightSnippet';

/**
 * Renders one InstantSearch hit through the storefront's existing ProductCard so
 * the InstantSearch results grid is visually identical to the rest of the SPA.
 * A hit is the `/srv/search` goodsList item (spread by the search client) plus an
 * `objectID`; ProductCard already reads both the OCS (`goodsName`/`goodsId`) and
 * the home/list (`name`/`id`) field shapes, so it can consume the hit directly.
 *
 * Wave-9 contract: a hit MAY carry an optional `highlight` map
 * `{field: snippet}` with matches `<em>`-wrapped. When the title field has a
 * snippet it is rendered through `renderSnippet` (em/mark become elements,
 * everything else stays escaped text — no raw HTML ever); absent map or field
 * means the plain title renders exactly as before.
 */

// Title-field candidates across the OCS / goods DTO shapes.
const TITLE_FIELDS = ['name', 'goodsName', 'goods_name', 'title'];

const titleSnippet = (hit: unknown): React.ReactNode | undefined => {
  const highlight = (hit as { highlight?: unknown })?.highlight;
  if (highlight == null || typeof highlight !== 'object') return undefined;
  for (const field of TITLE_FIELDS) {
    const snippet = (highlight as Record<string, unknown>)[field];
    if (typeof snippet === 'string' && snippet.trim()) return renderSnippet(snippet);
  }
  return undefined;
};

const ProductHit: React.FC<{ hit: IGood & { objectID: string } }> = ({ hit }) => (
  <ProductCard product={hit} nameNode={titleSnippet(hit)} />
);

export default ProductHit;
