import React, { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { fetchCart, syncLocalCart, updateCartItem } from 'app/shared/reducers/cartSlice';
import { CellGroup, EmptyState, GoodsLineCard, Page, PageHead, SubmitBar } from 'app/components/commonComponents/storefront';
import { fpTrack, numericGoodsId } from 'app/shared/tracking/firstParty';

/**
 * Shopping cart — litemall-vue `tabbar-cart` layout: a list of goods line-cards
 * (thumb, name, spec chips, price, a quantity stepper, remove) and a sticky
 * bottom submit-bar carrying the running total and the "Checkout" action. The
 * cart slice stays the single source of truth for the line items checkout sends.
 */
const CartView: React.FC = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();

  const { cartList } = useAppSelector(state => state.cart.data);
  const { isAuthenticated } = useAppSelector(state => state.customerAuth.data);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    dispatch(fetchCart()).finally(() => setIsLoading(false));
  }, [dispatch]);

  const total = useMemo(() => cartList.reduce((sum, item) => sum + (item.price ?? 0) * (item.number ?? 0), 0), [cartList]);

  const setQuantity = (id: number, qty: number) => {
    if (qty < 1) return;
    const updated = cartList.map(item => (item.id === id ? { ...item, number: qty } : item));
    if (isAuthenticated) {
      dispatch(updateCartItem({ id, number: qty })).then(() => dispatch(syncLocalCart(updated)));
    } else {
      dispatch(syncLocalCart(updated));
    }
  };

  const removeItem = (id: number) => {
    const removed = cartList.find(item => item.id === id);
    const goodsId = numericGoodsId(removed?.goodsId);
    if (goodsId) fpTrack('remove_from_cart', { goodsId, payload: { qty: removed?.number } });
    dispatch(syncLocalCart(cartList.filter(item => item.id !== id)));
  };

  if (isLoading) {
    return (
      <Page>
        <div className='text-center my-5'>
          <span className='spinner-border' role='status' />
        </div>
      </Page>
    );
  }

  if (cartList.length === 0) {
    return (
      <Page>
        <PageHead title='Shopping Cart' />
        <div className='container'>
          <CellGroup>
            <EmptyState icon='bi-cart-x' text='Your cart is empty.'>
              <Link to='/' className='btn btn-lm-primary'>
                Continue shopping
              </Link>
            </EmptyState>
          </CellGroup>
        </div>
      </Page>
    );
  }

  return (
    <Page>
      <PageHead title='Shopping Cart' sub={`You have ${cartList.length} item${cartList.length > 1 ? 's' : ''} in your cart`} />
      <div className='container'>
        <CellGroup>
          {cartList.map(item => {
            const id = item.id ?? 0;
            const qty = item.number ?? 0;
            return (
              <GoodsLineCard
                key={id}
                picUrl={item.picUrl}
                name={item.goodsName}
                to={item.goodsId ? `/product/${item.goodsId}` : undefined}
                specs={item.specifications}
                price={(item.price ?? 0) * qty}
                qtyControl={
                  <div className='input-group input-group-sm' style={{ width: 110 }}>
                    <button className='btn btn-lm-outline' type='button' onClick={() => setQuantity(id, qty - 1)} disabled={qty <= 1}>
                      <i className='bi bi-dash-lg' />
                    </button>
                    <input type='text' className='form-control text-center' value={qty} readOnly />
                    <button className='btn btn-lm-outline' type='button' onClick={() => setQuantity(id, qty + 1)}>
                      <i className='bi bi-plus-lg' />
                    </button>
                  </div>
                }
                trailing={
                  <button className='btn btn-sm btn-lm-outline ms-2' onClick={() => removeItem(id)} aria-label='Remove'>
                    <i className='bi bi-trash' />
                  </button>
                }
              />
            );
          })}
        </CellGroup>

        <div className='lm-amount--success small mb-2'>
          <i className='bi bi-truck' /> Free delivery within 1–2 weeks
        </div>
      </div>

      <SubmitBar total={total} buttonText='Checkout' onSubmit={() => navigate('/checkout')} />
    </Page>
  );
};

export default CartView;
