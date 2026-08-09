import Breadcrumb from 'app/components/userComponents/Breadcrumb';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import { CategoryData } from 'app/shared/model/category/category.models';
import { IGood } from 'app/shared/model/product/product.model';
import { productPath } from 'app/shared/util/slug';
import { money } from 'app/shared/util/money';
import React, { useEffect, useState } from 'react';
import { Button, Card, Col, Container, Row } from 'react-bootstrap';
import { Link, useParams } from 'react-router-dom';
import { getCurrentCatalogData, GoodCategoryResult } from './categorySlice';

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
interface CategoryListProps {}
type RouteParams = Record<string, string>;

const CategoryList: React.FC<CategoryListProps> = () => {
  const dispatch = useAppDispatch();
  const [currentData, setCurrentData] = useState<IGood[]>([]);
  const [goodCategoryResult, setGoodCategoryResult] = useState<GoodCategoryResult>(null);
  const [filterCriteria, setFilterCriteria] = useState<{ price?: [number, number] }>(null);
  const { categoryId } = useParams<{ categoryId: string }>();
  const products = useAppSelector(state => state.product.data.list);
  const { currentCatalogData, dataSecondCategories, defaultFirstGoodsSubCategory } = useAppSelector(state => state.category.data);

  const productsByCategory = useAppSelector(state => state.category.data.dataGoodsByCategoryId);
  const categoryFilterList = useAppSelector(state => state.product.data.filterCategoryList);

  const [view, setView] = useState<'grid' | 'list'>('list');

  // Data and Current data for data filtering
  const [data, setData] = useState<IGood[]>([]);

  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    dispatch(getCurrentCatalogData(parseInt(categoryId)));
  }, [categoryId]);

  return (
    <React.Fragment>
      <Container className='product-categories mt-4'>
        <Breadcrumb />
        {/* {currentCatalogData && <p>{JSON.stringify(currentCatalogData)}</p>} */}
        <h2>Top Categories</h2>
        <Row>
          {currentCatalogData.currentSubCategory.map((subCategoryId, index) => (
            <Col md={3} key={index} className='mb-4'>
              <Link to={`/category/${categoryId}/${subCategoryId.id}`} className='text-decoration-none'>
                <Card>
                  <Card.Img variant='top' src={subCategoryId.picUrl} />
                  <Card.Body>
                    <Card.Title>{subCategoryId.name}</Card.Title>
                  </Card.Body>
                </Card>
              </Link>
            </Col>
          ))}
        </Row>
        {error && <p style={{ color: 'red' }}>{error}</p>}
        <Row className='product-grid'>
          {currentData.map(product => (
            <Col key={product.id} xs={12} className='mb-4'>
              <Card className='product-card h-100 border-0 shadow-sm'>
                <Row noGutters>
                  <Col md={4}>
                    <div className='product-image-wrapper position-relative'>
                      <Card.Img src={product.picUrl} className='product-image h-100 object-fit-cover' />
                      {product.isNew && <span className='badge bg-success position-absolute top-0 start-0 m-2'>New</span>}
                      {product.isHot && <span className='badge bg-danger position-absolute top-0 end-0 m-2'>Hot</span>}
                    </div>
                  </Col>
                  <Col md={8}>
                    <Card.Body className='d-flex flex-column h-100'>
                      <Card.Title className='product-title h5 mb-2'>{product.name}</Card.Title>
                      <Card.Text className='product-description small text-muted mb-2'>{product.brief}</Card.Text>
                      <div className='mt-auto'>
                        <div className='d-flex justify-content-between align-items-center mb-2'>
                          <span className='product-price h4 mb-0 text-success'>{money(product.retailPrice)}</span>
                          <span className='product-original-price text-muted small'>
                            <del>{money(product.counterPrice)}</del>
                          </span>
                        </div>
                        <div className='d-flex justify-content-between align-items-center'>
                          <Link to={productPath(product.id ?? '', product.name)} className='btn btn-primary btn-sm'>
                            <Button variant='outline-primary' size='sm'>
                              View Deal
                            </Button>
                          </Link>
                          <span className='text-muted small'>Limited time offer</span>
                        </div>
                      </div>
                    </Card.Body>
                  </Col>
                </Row>
              </Card>
            </Col>
          ))}
        </Row>
      </Container>
    </React.Fragment>
  );
};

export default CategoryList;
