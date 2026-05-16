import Paging, { PaginationData } from 'app/components/userComponents/Paging';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import CustomTable, { TableData } from 'app/helpers/CustomTable';
import { IGood } from 'app/shared/model/product/product.model';
import { getAdminGoodsList } from 'app/shared/reducers/private/catalogMgn/adminGoodsSlice';
import React, { useEffect, useMemo, useState } from 'react';
import { Breadcrumb } from 'react-bootstrap';

const sortData = (data: TableData[], key: keyof TableData, direction: 'asc' | 'desc') => {
  return [...data].sort((a, b) => {
    if (a[key] < b[key]) return direction === 'asc' ? -1 : 1;
    if (a[key] > b[key]) return direction === 'asc' ? 1 : -1;
    return 0;
  });
};
const GoodsListView: React.FC = () => {
  const [view, setView] = useState<'grid' | 'list'>('list');
  const [limit, setLimit] = useState<number>(20);
  const [sort, setSort] = useState<string>('retail_price');
  const [order, setOrder] = useState<'desc' | 'asc'>('desc');
  const [page, setPage] = useState<number>(1);
  const [localSort, setLocalSort] = useState<{ key: keyof TableData; direction: 'asc' | 'desc' }>({ key: 'id', direction: 'desc' });

  const dispatch = useAppDispatch();
  const { adminGoodsResultList } = useAppSelector(state => state.private.adminGoods.data);

  const mapIGoodToTableData = (goods: IGood) => ({
    id: goods.id.toString(),
    name: goods.name,
    price: goods.retailPrice,
    picUrl: goods.picUrl,
  });

  const goodsColumns = [
    { key: 'id', header: 'ID', sortable: true },
    { key: 'name', header: 'Name', sortable: true },
    { key: 'price', header: 'Price', render: (item: TableData) => `${item.price.toFixed(2)}`, sortable: true },
    { key: 'picUrl', header: 'Image', render: (item: TableData) => <img src={item.picUrl} alt={item.name} style={{ width: '50px', height: '50px' }} /> },
  ];

  const goodsActions = [
    { name: 'Edit', action: (item: TableData) => console.log('Edit', item) },
    { name: 'View', action: (item: TableData) => console.log('View', item) },
    { name: 'Delete', action: (item: TableData) => console.log('Delete', item) },
  ];
  const onPageChanged = (pageData: PaginationData) => {
    const { currentPage, pageLimit } = pageData;
    setPage(currentPage);
    setLimit(pageLimit);
    dispatch(getAdminGoodsList({ page: currentPage, limit: pageLimit, sort: sort, order: order }));
  };

  useEffect(() => {
    dispatch(getAdminGoodsList({ limit, page, sort, order }));
  }, [dispatch, page, limit, sort, order]);

  const handleSortChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const [newSort, newOrder] = e.target.value.split('-');
    setSort(newSort);
    setOrder(newOrder as 'desc' | 'asc');
  };

  const handleLocalSort = (key: keyof TableData, direction: 'asc' | 'desc') => {
    setLocalSort({ key, direction });
  };

  const sortedData = useMemo(() => {
    return sortData(adminGoodsResultList.list.map(mapIGoodToTableData), localSort.key, localSort.direction);
  }, [adminGoodsResultList.list, localSort]);

  useEffect(() => {
    sortData(sortedData, sort, order);
  }, [adminGoodsResultList.list]);

  return (
    <React.Fragment>
      <div
        className='p-5 bg-primary bs-cover'
        style={{
          backgroundImage: 'url(../../images/banner/50-Banner.webp)',
        }}
      >
        <div className='container text-center'>
          <span className='display-5 px-3 bg-white rounded shadow'>T-Shirts</span>
        </div>
      </div>
      <Breadcrumb />
      <div className='container-fluid mb-3'>
        <div className='row'>
          <div className='col-md-12'>
            <div className='row'>
              <div className='col-7'>
                <span className='align-middle fw-bold'>
                  {adminGoodsResultList.total} results for <span className='text-warning'>t-shirts</span>
                </span>
              </div>
              <div className='col-5 d-flex justify-content-end'>
                <select className='form-select mw-180 float-start' aria-label='Default select' onChange={handleSortChange} value={`${sort}-${order}`}>
                  <option value='popularity-desc'>Most Popular</option>
                  <option value='add_time-desc'>Latest items</option>
                  <option value='trending-desc'>Trending</option>
                  <option value='retail_price-asc'>Higher price</option>
                  <option value='retail_price-desc'>Lower price</option>
                </select>
                <div className='btn-group ms-3' role='group'>
                  <button
                    aria-label='Grid'
                    type='button'
                    onClick={() => setView('grid')}
                    className={`btn ${view === 'grid' ? 'btn-primary' : 'btn-outline-primary'}`}
                  >
                    <i className='bi bi-grid' />
                  </button>
                  <button
                    aria-label='List'
                    type='button'
                    onClick={() => setView('list')}
                    className={`btn ${view === 'list' ? 'btn-primary' : 'btn-outline-primary'}`}
                  >
                    <i className='bi bi-list' />
                  </button>
                </div>
              </div>
            </div>
            <hr />
            <div className='row g-3'>
              <div className='col-md-9'>
                {view === 'list' && (
                  <CustomTable data={sortedData} columns={goodsColumns} actions={goodsActions} onSort={handleLocalSort} currentSort={localSort} />
                )}
              </div>
            </div>
            <hr />
            <Paging
              totalRecords={adminGoodsResultList.total}
              pageLimit={limit}
              pageNeighbours={3}
              onPageChanged={onPageChanged}
              sizing=''
              alignment='justify-content-center'
            />
          </div>
        </div>
      </div>
    </React.Fragment>
  );
};

export default GoodsListView;
