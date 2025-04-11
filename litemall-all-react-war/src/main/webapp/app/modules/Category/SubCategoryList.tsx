import FilterClear from 'app/components/commonComponents/filter/Clear';
import FilterColor from 'app/components/commonComponents/filter/Color';
import FilterPrice from 'app/components/commonComponents/filter/Price';
import FilterSize from 'app/components/commonComponents/filter/Size';
import FilterStar from 'app/components/commonComponents/filter/Star';
import FilterTag from 'app/components/commonComponents/filter/Tag';
import Breadcrumb from 'app/components/userComponents/Breadcrumb';
import CardServices from 'app/components/userComponents/card/CardServices';
import { useAppDispatch, useAppSelector } from 'app/config/hooks';
import { CategoryData } from 'app/shared/model/category/category.models';
import { IGood } from 'app/shared/model/product/product.model';
import React, { useEffect, useState } from 'react';
import { Button, Card, Col, Container, Row } from 'react-bootstrap';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { getCurrentCatalogData, GoodCategoryResult, goodsBySubCategoryId } from './categorySlice';

import { BASE_URL_CONTEXT } from 'app/config/api';
import axios from 'axios';
import './SubCategory.scss';

const usePagination = (data: IGood[], itemsPerPage: number) => {
  const [currentPage, setCurrentPage] = useState<number | null>(1);

  const maxPages = Math.ceil(data.length / itemsPerPage);

  const currentData = data.slice((currentPage - 1) * itemsPerPage, currentPage * itemsPerPage);
  const nextPage = () => setCurrentPage(page => Math.min(page + 1, maxPages));
  const prevPage = () => setCurrentPage(page => Math.max(page - 1, 1));

  return { currentData, nextPage, prevPage, currentPage, maxPages };
};
interface FilterDataResult {
  currentCategory: CategoryData;
  goodsCategory: IGood[];
}
interface ProductListViewProps {}
type RouteParams = Record<string, string>;

const SubCategoryList: React.FC<ProductListViewProps> = () => {
  // useParams from parent data through navigation
  const { categoryId, subCategoryId } = useParams<{ categoryId: string; subCategoryId: string }>();

  const dispatch = useAppDispatch();
  const navigate = useNavigate();

  //Internal state data
  const [currentData, setCurrentData] = useState<IGood[]>([]);
  const [goodCategoryResult, setGoodCategoryResult] = useState<GoodCategoryResult>(null);
  const [filterCriteria, setFilterCriteria] = useState<{ price?: [number, number] }>(null);
  const [currentPage, setCurrentPage] = useState<number | null>(null);
  const [itemsPerPage, setItemsPerPage] = useState<number | null>(null);
  const [visibleProducts, setVisibleProducts] = React.useState<number>(10);
  const [currentProducts, setCurrentProducts] = useState<IGood[]>([]);
  const [totalPages, setTotalPages] = useState<number | null>(null);
  const [totalItems, setTotalItems] = useState<number>(0);
  const [view, setView] = useState<'grid' | 'list'>('list');

  //UseAppSelector data
  const { currentCatalogData, dataGoodsByCategoryId } = useAppSelector(state => state.category.data);
  const productsBySubCategory = useAppSelector(state => state.category.data.dataGoodsByCategoryId);
  const categoryFilterList = useAppSelector(state => state.product.data.filterCategoryList);
  const products = useAppSelector(state => state.product.data.list);

  //Data and Current data for data filtering
  const [data, setData] = useState<IGood[]>([]);
  const [filters, setFilters] = useState({
    price: null as [number, number] | null,
    size: null,
    color: null,
    star: null,
    tag: null,
  });
  const [loadingVisible, setLoadingVisible] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const updateFilter = (filterType: keyof typeof filters, value: unknown) => {
    setFilters(prevFilters => ({ ...prevFilters, [filterType]: value }));
  };

  const loadMoreProducts = () => {
    if (loadingVisible) return;
    setLoadingVisible(true);

    setTimeout(() => {
      setVisibleProducts(prev => prev + 4);
      setLoadingVisible(false);
    }, 500);
  };

  const handleScroll = () => {
    if (window.innerHeight + window.scrollY >= document.body.offsetHeight - 100) {
      loadMoreProducts();
    }
  };

  /*  const onPageChanged = (page: PaginationData): void => {
    const { currentPage, totalPages, pageLimit } = page;
    const offset = (currentPage - 1) * pageLimit;
    if (productsByCategory) {
      const currentProducts = productsByCategory.goodsCategory.slice(offset, offset + pageLimit);
    }
    const currentProducts = products.slice(offset, offset + pageLimit);
    setCurrentPage(currentPage);
    setCurrentProducts(currentProducts);
    setTotalPages(totalPages);
  }; */

  const handleFetch = async (currentSubCategoryId: number) => {
    setLoadingVisible(true);
    setError(null);
    clearFilters(); // Reset filters when fetching new data

    try {
      const goodCategoryIdUrl = BASE_URL_CONTEXT + `/catalog/goods?id=${currentSubCategoryId}`;
      const response = await axios.get(goodCategoryIdUrl);
      const result = response.data.data as FilterDataResult;
      setData(result.goodsCategory);
      //console.log('current subcategory result: ', result.currentCategory);
      setCurrentData(result.goodsCategory);
    } catch (error) {
      setError('Failed to fetch products');
      setCurrentData([]);
    } finally {
      setLoadingVisible(false);
    }
  };

  const applyFilters = (data: IGood[]) => {
    return data.filter(item => {
      if (filters.price && Array.isArray(filters.price) && filters.price.length === 2) {
        const [minPrice, maxPrice] = filters.price;
        if (item.retailPrice < minPrice || item.retailPrice > maxPrice) {
          return false;
        }
      }
      return true;
    });
  };

  useEffect(() => {
    if (subCategoryId) {
      handleFetch(parseInt(subCategoryId));
    }
    if (categoryId) {
      dispatch(getCurrentCatalogData(parseInt(categoryId)));
    }

    if (categoryId && subCategoryId) {
      dispatch(goodsBySubCategoryId(parseInt(subCategoryId)));
    }
    window.addEventListener('scroll', handleScroll);
    //block for infinite scroll
    //const fiteredData = applyFilters(data);
    console.log('filters : ', filters);
    //console.log('the data goods category id', dataGoodsByCategoryId);
    //console.log('Filtered data: ', fiteredData);
    //setCurrentData(fiteredData);
    return () => {
      window.removeEventListener('scroll', handleScroll);
    };
  }, [subCategoryId, categoryId, dispatch]);

  /* useEffect(() => {
    if (dataGoodsByCategoryId) {
      console.log('dataGoodsByCategoryId: ', dataGoodsByCategoryId);
      const filteredData = applyFilters(dataGoodsByCategoryId.goodsCategory);
      setCurrentData(filteredData);
      console.log('filteredData: ', filteredData);
    }
  }, [filters, dataGoodsByCategoryId]); */
  /*  useEffect(() => {
    const fetchData = async () => {
      try {
        //await dispatch(getCurrentCatalogData(parseInt(categoryId)));
        await dispatch(getGoodsOfDefaultFirstSubCategory(parseInt(categoryId))).unwrap();

        //setLoading(false);
      } catch (error) {
        console.log('Error:', error);
        setError('Failed to fetch data');
        setLoading(false);
      }
    };

    fetchData();
    dispatch(goodsBySubCategoryId(parseInt(subCategoryId)));
  }, [categoryId, subCategoryId, dispatch]);
 */
  // Useful for applying filters
  useEffect(() => {
    const fiteredData = applyFilters(data);
    //console.log('filters : ', filters);
    console.log('the current subcategory', currentCatalogData.currentSubCategory);
    console.log('Filtered data: ', fiteredData);
    //console.log('Filtered data: ', fiteredData);
    setCurrentData(fiteredData);

    //setCurrentData(fiteredData);
  }, [filters, data]);

  const clearFilters = () => {
    setFilters({
      price: null,
      size: null,
      color: null,
      star: null,
      tag: null,
    });
  };

  //Sanitizing currentData with data

  /* const onChangeView = (view: 'grid' | 'list') => {
    setView(view);
  }; */

  return (
    <React.Fragment>
      <Container className='container mt-3'>
        <Breadcrumb />
      </Container>
      <Container className='container mt-4'>
        <Row>
          <Col md={3}>
            {loadingVisible ? (
              <p>Loading...</p>
            ) : currentCatalogData?.currentSubCategory.length > 0 ? (
              <div className='mb-4'>
                <div className='d-flex flex-wrap'>
                  {currentCatalogData?.currentSubCategory.map(
                    subCategory => (
                      console.log('subCategory data: ', subCategory),
                      (
                        <Button
                          key={subCategory.id}
                          onClick={() => handleFetch(subCategory.id)}
                          //This does not work for displaying names inline
                          /* className='d-block w-100 text-start mb-2' */
                          //these two following solutions work with subcategory-button the best
                          className='subcategory-button'
                          /* className='me-2 mb-2' */
                          variant='outline-primary'
                        >
                          {subCategory.name}
                        </Button>
                      )
                    )
                  )}
                </div>
              </div>
            ) : (
              <Col md={12}>
                <div className='d-flex justify-content-center align-items-center' style={{ minHeight: '100px' }}>
                  <p style={{ fontFamily: 'Helvetica Neue, Arial, sans-serif', fontSize: '14px', font: 'bold' }}> No SubCategory</p>
                </div>
              </Col>
            )}

            <FilterPrice onFilterChange={range => updateFilter('price', range)} />
            <FilterSize />
            <FilterStar />
            <FilterColor />
            <FilterClear />
            <FilterTag />
            <CardServices />
          </Col>

          <Col md={9}>
            {/* {loading && <p>Loading...</p>} */}
            {error && <p className='text-danger'>{error}</p>}

            {/*  {!loading && !error && ( */}
            <Row>
              {currentData.slice(0, visibleProducts).map((product, idx) => (
                <Col key={idx} xs={12} md={3} className='mb-4'>
                  <Link to={`/product/${product.id}`} className='text-decoration-none'>
                    <Card className='h-100 deal-card shadow-sm'>
                      <div className='image-container'>
                        <Card.Img variant='top' src={product.picUrl} className='product-image' />
                        <div className='badge-container'>
                          {product.isNew && <div className='badge badge-new'>New</div>}
                          {product.isHot && <div className='badge badge-hot'>Hot</div>}
                        </div>
                      </div>
                      <Card.Body className='d-flex flex-column'>
                        <Card.Title
                          className='product-title h6 mb-2'
                          style={{ fontSize: '14px', fontWeight: 'bold', lineHeight: '1.2', fontFamily: 'Helvetica Neue, Arial, sans-serif' }}
                        >
                          {product.name}
                        </Card.Title>
                        <Card.Text
                          className='product-brief flex-grow-1 mb-3'
                          style={{
                            fontSize: '13px',
                            fontFamily: 'Helvetica Neue, Arial, sans-serif',
                            lineHeight: '1.4',
                            maxHeight: '3.9em',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            display: '-webkit-box',
                            WebkitLineClamp: 3,
                            WebkitBoxOrient: 'vertical',
                            color: '#666',
                          }}
                        >
                          {product.brief}
                        </Card.Text>
                        <div className='mt-auto'>
                          <div className='d-flex flex-wrap align-items-end justify-content-between'>
                            <div className='price-container flex-grow-1 me-2 mb-2'>
                              <div className='d-flex align-items-baseline'>
                                <span className='current-price me-2' style={{ fontSize: '1.1rem', fontWeight: 'bold', color: '#e53935' }}>
                                  ${product.retailPrice}
                                </span>
                                <span className='original-price text-muted' style={{ fontSize: '0.9rem', textDecoration: 'line-through' }}>
                                  ${product.counterPrice}
                                </span>
                              </div>
                              <div className='discount-badge'>
                                {Math.round(((product.counterPrice - product.retailPrice) / product.counterPrice) * 100)}% OFF
                              </div>
                            </div>
                            {/*  <button className='btn btn-primary btn-sm view-deal-button'>Detail</button> */}
                          </div>
                        </div>
                      </Card.Body>
                    </Card>
                  </Link>
                </Col>
              ))}
            </Row>
            {loadingVisible && <div className='text-center'>Loading more products...</div>}
            {!loadingVisible && currentData.length === 0 && (
              <Col md={12}>
                <div className='d-flex justify-content-center align-items-center' style={{ minHeight: '200px' }}>
                  <p style={{ fontFamily: 'Helvetica Neue, Arial, sans-serif', fontSize: '24px', font: 'bold' }}>Oops No products found.</p>
                </div>
              </Col>
            )}
          </Col>
        </Row>
      </Container>
    </React.Fragment>
  );
};

export default SubCategoryList;
