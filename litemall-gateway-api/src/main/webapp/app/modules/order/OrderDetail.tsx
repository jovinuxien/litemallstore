import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Spinner } from 'react-bootstrap';
import { Link, useNavigate, useParams } from 'react-router-dom';

import { priceNum } from 'app/components/userComponents/card/ProductCard';
import { Cell, CellGroup, EmptyState, GoodsLineCard, OrderSummary, Page, PageHead } from 'app/components/commonComponents/storefront';
import { actionErrorMessage, orderApi } from 'app/shared/api';
import { useTranslation } from 'app/i18n';
import { IOrderDetail, IStore } from 'app/shared/model/order/order.model';
import { orderAddressLines } from 'app/shared/util/address';
import { money } from 'app/shared/util/money';
import { QRCodeSVG } from 'qrcode.react';

import ReviewForm from 'app/modules/product/productDetailComponent/ReviewForm';
import AftersalePanel from './AftersalePanel';
import DisputePanel from './DisputePanel';
import TrackingPanel from './TrackingPanel';
import './order.scss';

/** verifyTime arrives as a LocalDateTime tuple ([y,m,d,h,min,...]) or an ISO string. */
const fmtDateTime = (t?: string | number[]): string => {
  if (Array.isArray(t) && t.length >= 5) {
    const [y, m, d, h, min] = t;
    return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')} ${String(h).padStart(2, '0')}:${String(min).padStart(2, '0')}`;
  }
  return typeof t === 'string' ? t.slice(0, 16).replace('T', ' ') : '';
};

/**
 * Single-order view, modelled on litemall-vue `order/order-detail`: shipping
 * recipient, line items, and the price breakdown (goods / freight / coupon /
 * actual). Sourced from `/srv/order/detail`. Graceful when not live yet.
 */
const OrderDetailView: React.FC = () => {
  const { t } = useTranslation('order');
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [order, setOrder] = useState<IOrderDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [missing, setMissing] = useState(false);
  const [pending, setPending] = useState(false);
  // A refused action's reason, the server's words (lifecycle contract §3: verbatim).
  const [actionError, setActionError] = useState<string | null>(null);
  // Which goods line has its review form open (handleOption.comment orders).
  const [reviewingGoodsId, setReviewingGoodsId] = useState<number | string | null>(null);
  // Pickup orders carry only storeId — the store card is fetched separately
  // (handoff-gateway-api-pickup.md). Fetch failure/hidden store → no card.
  const [pickupStore, setPickupStore] = useState<IStore | null>(null);

  const fetchDetail = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    try {
      const d = await orderApi.detail(id);
      setOrder(d ?? null);
    } catch {
      setMissing(true);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    fetchDetail();
  }, [fetchDetail]);

  useEffect(() => {
    if (order?.deliveryType === 'pickup' && order.storeId != null) {
      orderApi
        .storeDetail(order.storeId)
        .then(s => setPickupStore(s ?? null))
        .catch(() => setPickupStore(null));
    } else {
      setPickupStore(null);
    }
  }, [order?.deliveryType, order?.storeId]);

  // Run an order action (cancel/confirm/refund/delete) then refresh the detail.
  const act = async (fn: () => Promise<unknown>) => {
    setPending(true);
    setActionError(null);
    try {
      await fn();
      await fetchDetail();
    } catch (e) {
      setActionError(actionErrorMessage(e));
    } finally {
      setPending(false);
    }
  };

  if (loading) {
    return (
      <Page>
        <PageHead title={t('detail.title')} />
        <div className='text-center my-5'>
          <Spinner animation='border' />
        </div>
      </Page>
    );
  }

  if (missing || !order) {
    return (
      <Page>
        <PageHead title={t('detail.title')} />
        <div className='container'>
          <CellGroup>
            <EmptyState icon='bi-receipt' text={t('detail.unavailable')}>
              <Link to='/orders' className='btn btn-lm-outline'>
                {t('actions.backToOrders')}
              </Link>
            </EmptyState>
          </CellGroup>
        </div>
      </Page>
    );
  }

  const opt = order.handleOption;
  const orderId = order.id;

  return (
    <Page>
      <PageHead title={t('detail.title')} sub={order.orderStatusText} />
      <div className='container'>
        {/* Goods — an "Unrated" order (handleOption.comment) offers a per-item review form. */}
        <CellGroup title={t('detail.items')}>
          {(order.orderGoods ?? []).map(g => (
            <React.Fragment key={g.id}>
              <GoodsLineCard
                picUrl={g.picUrl}
                name={g.goodsName}
                to={g.goodsId ? `/product/${g.goodsId}` : undefined}
                specs={g.specifications}
                price={priceNum(g.price)}
                qty={g.number}
              />
              {opt?.comment && g.goodsId != null && (
                <div className='px-3 pb-3'>
                  {reviewingGoodsId === g.goodsId ? (
                    <ReviewForm goodsId={g.goodsId} />
                  ) : (
                    <button type='button' className='btn btn-sm btn-lm-outline' onClick={() => setReviewingGoodsId(g.goodsId ?? null)}>
                      <i className='bi bi-star me-1' />
                      {t('actions.writeReview')}
                    </button>
                  )}
                </div>
              )}
            </React.Fragment>
          ))}
        </CellGroup>

        {/* Money */}
        <CellGroup>
          <OrderSummary
            rows={[
              { label: t('detail.goodsTotal'), value: money(priceNum(order.goodsPrice)) },
              {
                label: t('detail.shipping'),
                value: priceNum(order.freightPrice) > 0 ? money(priceNum(order.freightPrice)) : t('detail.free'),
                variant: 'muted',
              },
              ...(priceNum(order.couponPrice) > 0
                ? [{ label: t('detail.coupon'), value: `−${money(priceNum(order.couponPrice))}`, variant: 'success' as const }]
                : []),
              { label: t('detail.totalPaid'), value: money(priceNum(order.actualPrice)), variant: 'total' },
            ]}
          />
        </CellGroup>

        {/* Pickup order: store card (fetched via /srv/store/detail) + verify
            code. verifyCode arrives once paid; verifyTime set = collected. */}
        {order.deliveryType === 'pickup' ? (
          <CellGroup title={t('detail.storePickup')}>
            {pickupStore && (
              <Cell title={pickupStore.name ?? t('detail.pickupStore')}>
                <span className='text-muted'>
                  {[pickupStore.address, pickupStore.detailedAddress].filter(Boolean).join(' ')}
                  {pickupStore.businessHours ? ` · ${pickupStore.businessHours}` : ''}
                  {pickupStore.phone ? ` · ${pickupStore.phone}` : ''}
                </span>
              </Cell>
            )}
            {order.verifyCode &&
              (order.verifyTime ? (
                <Cell
                  title={t('detail.collected')}
                  value={
                    <span className='text-success'>
                      <i className='bi bi-check-circle me-1' />
                      {fmtDateTime(order.verifyTime)}
                    </span>
                  }
                />
              ) : (
                <div className='p-3 text-center'>
                  <div className='text-muted small mb-1'>{t('detail.showCode')}</div>
                  <div className='fw-bold' style={{ fontSize: '1.8rem', letterSpacing: '0.25em' }}>{order.verifyCode}</div>
                  <div className='mt-2'>
                    <QRCodeSVG value={order.verifyCode} size={140} />
                  </div>
                </div>
              ))}
          </CellGroup>
        ) : (
          <CellGroup title={t('detail.deliveryAddress')}>
            {/* Structured snapshot (V48) renders as stacked lines; legacy
                unseparated rows fall back to one line. */}
            <div className='p-3'>
              <div className='fw-semibold'>
                {order.consignee ?? ''}
                {order.mobile ? <span className='text-muted fw-normal'> · {order.mobile}</span> : null}
              </div>
              <div className='text-muted mt-1'>
                {orderAddressLines(order.address).map(line => (
                  <div key={line}>{line}</div>
                ))}
              </div>
            </div>
          </CellGroup>
        )}

        {/* Order meta */}
        <CellGroup>
          {order.addTime && <Cell title={t('detail.orderTime')} value={order.addTime} />}
          <Cell title={t('detail.orderNo')} value={order.orderSn ?? order.id} />
          {order.source === 'cj' && <Cell title={t('detail.fulfilment')} value={<span className='badge bg-lm-primary'>{t('list.dropship')}</span>} />}
          {order.fulfillmentStatus && <Cell title={t('detail.fulfilmentStatus')} value={order.fulfillmentStatus} />}
          {order.shipChannel && <Cell title={t('detail.shipsVia')} value={order.shipChannel} />}
          {order.cjOrderNum && <Cell title={t('detail.cjOrderNo')} value={order.cjOrderNum} />}
          {order.orderStatusText && <Cell title={t('detail.status')} value={order.orderStatusText} />}
        </CellGroup>

        {/* Shipment tracking (Wave 4 — /srv/order/{id}/tracking; hides itself
            when the endpoint isn't deployed). Pickup orders don't ship. */}
        {orderId != null && order.deliveryType !== 'pickup' && <TrackingPanel orderId={orderId} />}

        {/* Aftersale / RMA (Wave-2 vertical; ImageUploader photos — Wave 4). */}
        {orderId != null && <AftersalePanel orderId={orderId} canApply={!!opt?.aftersale} onChanged={fetchDetail} />}

        {/* Dropship problems & disputes (CJ orders only, once paid = placed at CJ) */}
        {order.source === 'cj' && order.cjOrderId != null && orderId != null && <DisputePanel orderId={orderId} />}

        {/* Actions */}
        {opt && (opt.pay || opt.cancel || opt.confirm || opt.refund || opt.withdrawRefund || opt.delete) && (
          <CellGroup>
            {actionError && (
              <Alert variant='danger' className='m-3 mb-0 py-2 small' role='alert'>
                {actionError}
              </Alert>
            )}
            <div className='p-3 d-flex flex-wrap gap-2 justify-content-end'>
              {opt.pay && orderId != null && (
                <button type='button' className='btn btn-lm-primary btn-sm' disabled={pending} onClick={() => navigate(`/pay/${orderId}`)}>
                  {t('actions.payNow')}
                </button>
              )}
              {opt.cancel && orderId != null && (
                <button type='button' className='btn btn-lm-outline btn-sm' disabled={pending} onClick={() => act(() => orderApi.cancel(orderId))}>
                  {t('actions.cancel')}
                </button>
              )}
              {opt.confirm && orderId != null && (
                <button type='button' className='btn btn-lm-outline btn-sm' disabled={pending} onClick={() => act(() => orderApi.confirm(orderId))}>
                  {t('actions.confirmReceipt')}
                </button>
              )}
              {opt.refund && orderId != null && (
                <button type='button' className='btn btn-lm-outline btn-sm' disabled={pending} onClick={() => act(() => orderApi.refund(orderId))}>
                  {t('actions.refund')}
                </button>
              )}
              {opt.withdrawRefund && orderId != null && (
                <button type='button' className='btn btn-lm-outline btn-sm' disabled={pending} onClick={() => act(() => orderApi.withdrawRefund(orderId))}>
                  {t('actions.withdrawRefund')}
                </button>
              )}
              {opt.delete && orderId != null && (
                <button
                  type='button'
                  className='btn btn-lm-outline btn-sm'
                  disabled={pending}
                  onClick={() => act(async () => {
                    await orderApi.remove(orderId);
                    navigate('/orders');
                  })}
                >
                  {t('actions.delete')}
                </button>
              )}
            </div>
          </CellGroup>
        )}
      </div>
    </Page>
  );
};

export default OrderDetailView;
