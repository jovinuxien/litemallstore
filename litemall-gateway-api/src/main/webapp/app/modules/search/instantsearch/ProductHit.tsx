import React from 'react';

import ProductCard from 'app/components/userComponents/card/ProductCard';
import { IGood } from 'app/shared/model/product/product.model';

/**
 * Renders one InstantSearch hit through the storefront's existing ProductCard so
 * the InstantSearch results grid is visually identical to the rest of the SPA.
 * A hit is the `/srv/search` goodsList item (spread by the search client) plus an
 * `objectID`; ProductCard already reads both the OCS (`goodsName`/`goodsId`) and
 * the home/list (`name`/`id`) field shapes, so it can consume the hit directly.
 */
const ProductHit: React.FC<{ hit: IGood & { objectID: string } }> = ({ hit }) => <ProductCard product={hit} />;

export default ProductHit;
