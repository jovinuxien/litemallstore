//import { useGetAdminGoodsCatAndBrandQuery, useGetAdminGoodsListQuery } from 'app/shared/reducers/private/services/admingoodsrv/adminGoodsApi';
import React from 'react';

const AdminGoodsList: React.FC = () => {
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(10);
  const [sort, setSort] = React.useState('add_time');
  const [order, setOrder] = React.useState<'desc' | 'asc'>('desc');

  //const { data: goodsData, error: goodsError, isLoading: goodsLoading } = useGetAdminGoodsListQuery({ limit, page, sort, order });
  //const { data: catAndBrandData, error: catAndBrandError, isLoading: catAndBrandLoading } = useGetAdminGoodsCatAndBrandQuery();

  /* if (goodsLoading || catAndBrandLoading) {
    return <div>Loading...</div>;
  }

  if (goodsError || catAndBrandError) {
    return <div>Error occurred while fetching data.</div>;
  }

  return (
    <div>
      <h1>Admin Goods List</h1>
      {goodsData && goodsData.data && (
        <ul>
          {goodsData.data.list.map(good => (
            <li key={good.id}>{good.name}</li>
          ))}
        </ul>
      )}
      <h2>Categories and Brands</h2>
      {catAndBrandData && catAndBrandData.data && (
        <div>
          <h3>Categories</h3>
          <ul>
            {catAndBrandData.data.catList.map(cat => (
              <li key={cat.id}>{cat.name}</li>
            ))}
          </ul>
          <h3>Brands</h3>
          <ul>
            {catAndBrandData.data.brandList.map(brand => (
              <li key={brand.id}>{brand.name}</li>
            ))}
          </ul>
        </div>
      )}
    </div>
  ); */
  return <></>;
};

export default AdminGoodsList;
