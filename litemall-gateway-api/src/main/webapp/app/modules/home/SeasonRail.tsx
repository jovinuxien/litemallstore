import React, { useEffect, useState } from 'react';

import ProductCard, { goodId } from 'app/components/userComponents/card/ProductCard';
import { IGood } from 'app/shared/model/product/product.model';
import { firstGoodsList, seasonLabel, seasonPath } from 'app/shared/util/season';
import { useSeason } from 'app/shared/util/useSeason';
import { loadGoodsListGoods } from 'app/modules/page/goodsListSource';
import Section from './Section';

/**
 * Wave 27 — the home strip for the running season collection.
 *
 * Everything a shopper sees here is admin-owned data: the heading is the
 * season page's NAME and the products are its first `goods-list` component,
 * resolved by the same code that renders that page, so the rail is a genuine
 * preview of where "See more" leads rather than a second opinion about it.
 *
 * Renders NOTHING when no season is running, when the season has no goods
 * rail, or when that rail resolves empty — palette degrade rule R1 and the
 * spec's absent-not-empty rule agree: an empty grid under a season heading
 * advertises a collection that isn't there.
 */

/** Home rails show a handful; the season's own page shows the full rail. */
const RAIL_ITEMS = 8;

const SeasonRail: React.FC = () => {
  const season = useSeason();
  const [goods, setGoods] = useState<IGood[]>([]);

  const rail = firstGoodsList(season);

  useEffect(() => {
    let cancelled = false;
    if (!rail) {
      setGoods([]);
      return undefined;
    }
    loadGoodsListGoods(rail.config ?? {}, RAIL_ITEMS)
      .then(list => {
        if (!cancelled) setGoods(list);
      })
      .catch(() => {
        if (!cancelled) setGoods([]);
      });
    return () => {
      cancelled = true;
    };
  }, [rail]);

  const label = seasonLabel(season);
  if (!season || !label || goods.length === 0) return null;

  return (
    <Section title={label} moreTo={seasonPath(season)}>
      <div className="lm-rail">
        {goods.map((product, i) => (
          <ProductCard key={`season-${goodId(product) ?? i}`} product={product} />
        ))}
      </div>
    </Section>
  );
};

export default SeasonRail;
