import { cjPlacementState, orderStatusInfo, parkedLabel } from 'app/shared/model/admin/order.model';
import { useReadOrderQuery } from 'app/shared/reducers/private/services/adminCatalogApi';
import {
  ICjApprovalStamp,
  ITracking,
  orderOpMessage,
  toApprovalStamp,
  useApproveCjPlacementMutation,
  useRequeueCjPlacementMutation,
  useGetFulfillmentConfigQuery,
  useGetTrackingQuery,
  useMarkOrderPaidMutation,
  usePrintReceiptMutation,
} from 'app/shared/reducers/private/services/adminOrderCjApi';
import { money } from 'app/shared/util/money';
import { fromServerDateTime } from 'app/shared/util/server-datetime';
import { Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { useNavigate, useParams } from 'react-router-dom';

// Admin order detail — order summary, customer, shipping/pickup, line items,
// the logistics/tracking panel (order's trackInfo surface — CJ and, since
// Wave 4, locally-shipped orders too), plus fulfilment operations: offline
// mark-paid (which live-fires the CJ createOrderV2 replay for CJ orders) and
// receipt printing. Fetched per id from order-service
// (/srv/private/admin/order/detail) as an authenticated admin.


const UNAVAILABLE: ITracking = { available: false, shipped: false, events: [] };

// Logistics / tracking card, rendered for CJ orders AND any order the DTO
// marks as shipped (shipSn present). Degrades in layers: endpoint erroring →
// fall back to the order's own shipChannel/shipSn if shipped, else a muted
// note; live but not shipped → clean empty state; a Wave-4 `note`
// ("tracking provider disabled" / "tracking temporarily unavailable") → muted
// info line, never an error; shipped → carrier + tracking number + events.
const TrackingPanel: React.FC<{ orderId: string; shipChannel?: string; shipSn?: string }> = ({ orderId, shipChannel, shipSn }) => {
  const { data, isLoading, isError } = useGetTrackingQuery(orderId);
  const tracking = isError ? UNAVAILABLE : data;

  // Note text arrives machine-ish ("tracking provider disabled") — sentence-case it.
  const noteText = tracking?.note ? tracking.note.charAt(0).toUpperCase() + tracking.note.slice(1) : undefined;

  return (
    <div className='box-card mt-3'>
      <h6>Logistics / Tracking</h6>
      {isLoading ? (
        <span className='spinner-border spinner-border-sm text-primary' role='status' />
      ) : !tracking || !tracking.available ? (
        shipSn ? (
          <div className='small'>
            {shipChannel && (
              <span className='me-3'>
                Carrier: <strong>{shipChannel}</strong>
              </span>
            )}
            <span className='me-3'>
              Tracking #: <strong>{shipSn}</strong>
            </span>
            <div className='text-muted mt-1'>Live tracking is temporarily unavailable.</div>
          </div>
        ) : (
          <div className='text-muted small'>Tracking is temporarily unavailable.</div>
        )
      ) : !tracking.shipped && tracking.events.length === 0 && !tracking.trackNumber ? (
        <>
          {noteText && <div className='text-muted small mb-1'>{noteText}</div>}
          <div className='text-muted'>Not shipped yet — no tracking events.</div>
        </>
      ) : (
        <>
          <div className='small mb-2'>
            {tracking.carrier && (
              <span className='me-3'>
                Carrier: <strong>{tracking.carrier}</strong>
              </span>
            )}
            {tracking.trackNumber && (
              <span className='me-3'>
                Tracking #: <strong>{tracking.trackNumber}</strong>
              </span>
            )}
            {tracking.cjOrderStatus && <Tag tag='primary'>{tracking.cjOrderStatus}</Tag>}
          </div>
          {noteText && <div className='text-muted small mb-2'>{noteText}</div>}
          {tracking.events.length === 0 ? (
            !noteText && <div className='text-muted small'>No tracking events reported yet.</div>
          ) : (
            <table className='el-table'>
              <thead>
                <tr>
                  <th>Time</th>
                  <th>Status</th>
                  <th>Detail</th>
                  <th>Location</th>
                </tr>
              </thead>
              <tbody>
                {tracking.events.map((ev, i) => (
                  <tr key={i}>
                    <td className='text-muted small'>{ev.time ?? '—'}</td>
                    <td>{ev.status ?? '—'}</td>
                    <td>{ev.description ?? '—'}</td>
                    <td className='text-muted small'>{ev.location ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      )}
    </div>
  );
};

const OrderDetail: React.FC = () => {
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const { data, isLoading, isError, error, refetch } = useReadOrderQuery(id as string, { skip: id == null });

  // Fulfilment ops (Wave 4). Config is flags-only and read tolerantly — if it
  // is unavailable the print button stays enabled and errno 641 does the talking.
  const { data: fulfillment } = useGetFulfillmentConfigQuery();
  const [markPaid, { isLoading: paying }] = useMarkOrderPaidMutation();
  const [printReceipt, { isLoading: printing }] = usePrintReceiptMutation();
  const [approvePlacement, { isLoading: approving }] = useApproveCjPlacementMutation();

  const [payOpen, setPayOpen] = React.useState(false);
  const [payReference, setPayReference] = React.useState('');
  const [approveOpen, setApproveOpen] = React.useState(false);
  // Lifecycle package B: a parked order (CJ rejected / placement stalled) is
  // requeued from here; the sentinel clears server-side and the refetch moves
  // the state on. Approval is never offered for a parked order.
  const [requeuePlacement, { isLoading: requeuing }] = useRequeueCjPlacementMutation();
  const [requeueOpen, setRequeueOpen] = React.useState(false);
  // Stamp from a just-confirmed approval — keeps the UI honest even before
  // the refetched detail payload projects the V59 columns.
  const [localStamp, setLocalStamp] = React.useState<ICjApprovalStamp | null>(null);
  const [actionMsg, setActionMsg] = React.useState<{ ok: boolean; text: string } | null>(null);

  if (isLoading) {
    return (
      <div className='app-container text-center p-5'>
        <span className='spinner-border text-primary' role='status' />
      </div>
    );
  }

  const errStatus = (error as { status?: number | string })?.status;
  const order = data?.order;
  if (isError || !order) {
    return (
      <div className='app-container'>
        <div className='alert alert-danger'>Failed to load order{errStatus ? ` (${errStatus})` : ''}.</div>
        <button className='btn btn-outline-secondary' onClick={() => navigate(-1)}>
          ‹ Back
        </button>
      </div>
    );
  }

  const st = orderStatusInfo(order.orderStatus);
  const goods = data?.orderGoods ?? [];
  const user = data?.user;

  // Offline mark-paid is only legal from unpaid/created (101).
  const isUnpaid = order.orderStatus === 101;
  const isCj = order.source === 'cj';
  const printerDisabled = fulfillment?.available === true && fulfillment.printerEnabled === false;

  // Wave-23 manual CJ placement: paid CJ orders wait for admin approval; the
  // sweep places them on its next tick — never claim "placed" until
  // cjOrderId exists. A just-confirmed approval (localStamp) flips the state
  // even if the refetched payload doesn't project the stamp columns yet.
  const basePlacement = cjPlacementState(order);
  const placement = basePlacement === 'awaiting-approval' && localStamp ? 'approved-awaiting-placement' : basePlacement;
  const approvalStamp: ICjApprovalStamp | null =
    localStamp ??
    (order.cjPlacementApprovedTime != null && order.cjPlacementApprovedTime !== ''
      ? { approvedBy: order.cjPlacementApprovedBy, approvedTime: fromServerDateTime(order.cjPlacementApprovedTime) }
      : null);

  // Pickup block (deliveryType === 'pickup'): store name comes from an
  // explicit field or the "PICKUP: <name>" address convention, else storeId.
  const isPickup = order.deliveryType === 'pickup';
  const pickupStore = /^PICKUP:\s*(.+)$/.exec(order.address ?? '')?.[1] ?? (order.storeId != null ? `Store #${order.storeId}` : undefined);

  const onConfirmMarkPaid = async () => {
    setActionMsg(null);
    const res = await markPaid({ orderId: id as string, reference: payReference.trim() || undefined });
    const msg = orderOpMessage(res);
    if (msg) {
      setActionMsg({ ok: false, text: msg });
    } else {
      setActionMsg({ ok: true, text: `Order ${order.orderSn || `#${order.id}`} marked paid (offline).` });
      setPayOpen(false);
      setPayReference('');
      refetch();
    }
  };

  const onConfirmRequeue = async () => {
    setActionMsg(null);
    const res = await requeuePlacement(id as string);
    const msg = orderOpMessage(res);
    if (msg) {
      setActionMsg({ ok: false, text: msg });
    } else {
      const data = (res as { data?: { data?: { message?: string } } }).data?.data;
      setActionMsg({
        ok: true,
        text: `Order ${order.orderSn || `#${order.id}`}: ${data?.message || 'requeued for CJ placement — the sweep retries it on its next tick.'}`,
      });
      setRequeueOpen(false);
      refetch();
    }
  };

  const onConfirmApprove = async () => {
    setActionMsg(null);
    const res = await approvePlacement(id as string);
    const msg = orderOpMessage(res);
    if (msg) {
      setActionMsg({ ok: false, text: msg });
    } else {
      setLocalStamp(toApprovalStamp(res));
      setActionMsg({
        ok: true,
        text: `Order ${order.orderSn || `#${order.id}`} approved for CJ fulfilment — the placement sweep will submit it on its next tick.`,
      });
      setApproveOpen(false);
      refetch();
    }
  };

  const onPrintReceipt = async () => {
    setActionMsg(null);
    const res = await printReceipt(id as string);
    const errno = (res as { data?: { errno?: number; errmsg?: string } }).data?.errno;
    if (errno === 0) {
      setActionMsg({ ok: true, text: 'Receipt sent to the printer.' });
    } else if (errno === 641) {
      setActionMsg({ ok: false, text: 'Receipt printing is disabled.' });
    } else if (errno === 642) {
      setActionMsg({ ok: false, text: 'Print failed — check the printer.' });
    } else {
      setActionMsg({ ok: false, text: orderOpMessage(res) ?? 'Print request failed.' });
    }
  };

  return (
    <div className='app-container'>
      <div className='d-flex align-items-center justify-content-between mb-3'>
        <h4 className='m-0'>
          Order {order.orderSn || `#${order.id}`} <Tag tag={st.tag}>{order.orderStatusText || st.label}</Tag>
          {isCj && (
            <span className='ms-2'>
              <Tag tag='warning'>CJ dropship{order.cjOrderNum ? ` · ${order.cjOrderNum}` : ''}</Tag>
            </span>
          )}
          {placement === 'awaiting-approval' && (
            <span className='ms-2'>
              <Tag tag='danger'>Pending CJ approval</Tag>
            </span>
          )}
          {placement === 'approved-awaiting-placement' && (
            <span className='ms-2'>
              <Tag tag='info'>Approved — awaiting CJ placement</Tag>
            </span>
          )}
          {placement === 'parked' && (
            <span className='ms-2'>
              <Tag tag='danger'>{parkedLabel(order.cjOrderStatus) ?? 'Parked'}</Tag>
            </span>
          )}
          {isPickup && (
            <span className='ms-2'>
              <Tag tag='info'>Pickup</Tag>
            </span>
          )}
        </h4>
        <div>
          {placement === 'awaiting-approval' && (
            <button className='btn btn-warning btn-sm me-2' disabled={approving || approveOpen} onClick={() => setApproveOpen(true)}>
              Approve for CJ fulfilment
            </button>
          )}
          {placement === 'parked' && (
            <button className='btn btn-warning btn-sm me-2' disabled={requeuing || requeueOpen} onClick={() => setRequeueOpen(true)}>
              Requeue for CJ placement
            </button>
          )}
          {isUnpaid && (
            <button className='btn btn-outline-success btn-sm me-2' disabled={paying || payOpen} onClick={() => setPayOpen(true)}>
              Mark paid (offline)
            </button>
          )}
          <button
            className='btn btn-outline-secondary btn-sm me-2'
            disabled={printing || printerDisabled}
            title={printerDisabled ? 'Receipt printing is disabled in the fulfilment configuration.' : 'Print a receipt for this order'}
            onClick={onPrintReceipt}
          >
            {printing ? 'Printing…' : 'Print receipt'}
          </button>
          <button className='btn btn-outline-secondary btn-sm' onClick={() => navigate('/admin/mall/order')}>
            ‹ Back to orders
          </button>
        </div>
      </div>

      {actionMsg && <div className={`alert ${actionMsg.ok ? 'alert-success' : 'alert-danger'}`}>{actionMsg.text}</div>}

      {approvalStamp && placement !== 'awaiting-approval' && (
        <div className='text-muted small mb-2'>
          Approved for CJ fulfilment
          {approvalStamp.approvedBy ? ` by ${approvalStamp.approvedBy}` : ''}
          {approvalStamp.approvedTime ? ` at ${approvalStamp.approvedTime.replace('T', ' ')}` : ''}.
          {placement === 'approved-awaiting-placement' && ' Not yet placed at CJ — the placement sweep submits it on its next tick.'}
          {placement === 'parked' && ' The approval stands, but the order is parked — approval alone does nothing; use Requeue once the cause is fixed.'}
        </div>
      )}

      {placement === 'parked' && (
        <div className='alert alert-danger py-2'>
          <strong>{parkedLabel(order.cjOrderStatus) ?? 'Parked'}.</strong>{' '}
          {order.cjOrderStatus === 'PLACEMENT_STALLED'
            ? 'Placement kept failing and was parked after the stall window.'
            : 'CJ refused the fulfilment order.'}{' '}
          Fix the cause, then requeue — the placement sweep retries it on its next tick. CJ&apos;s exact words are on the pending tab
          (hold reason) and the order timeline.
        </div>
      )}

      {requeueOpen && (
        <div className='box-card mb-3' style={{ borderLeft: '4px solid #E6A23C' }}>
          <h6>Requeue for CJ placement</h6>
          <p className='mb-1'>
            Retry sending order <strong>{order.orderSn || `#${order.id}`}</strong> ({money(order.actualPrice)}) to CJ? Fix the cause first.
          </p>
          <p className='text-danger fw-bold mb-1'>
            The approval stamp is kept. If CJ accepts the order this time, the placement spends real fulfilment money from the CJ balance.
          </p>
          <div className='d-flex align-items-center gap-2 mt-2'>
            <button className='btn btn-warning btn-sm' disabled={requeuing} onClick={onConfirmRequeue}>
              {requeuing ? 'Requeuing…' : 'Confirm requeue'}
            </button>
            <button className='btn btn-outline-secondary btn-sm' disabled={requeuing} onClick={() => setRequeueOpen(false)}>
              Cancel
            </button>
          </div>
        </div>
      )}

      {approveOpen && (
        <div className='box-card mb-3' style={{ borderLeft: '4px solid #E6A23C' }}>
          <h6>Approve for CJ fulfilment</h6>
          <p className='mb-1'>
            Approve order <strong>{order.orderSn || `#${order.id}`}</strong> ({money(order.actualPrice)}) for CJ fulfilment?
          </p>
          <p className='text-danger fw-bold mb-1'>
            Approval sends this order to CJ Dropshipping for live placement and spends real fulfilment money. The placement sweep submits
            it on its next tick.
          </p>
          <div className='d-flex align-items-center gap-2 mt-2'>
            <button className='btn btn-warning btn-sm' disabled={approving} onClick={onConfirmApprove}>
              {approving ? 'Approving…' : 'Confirm approval'}
            </button>
            <button className='btn btn-outline-secondary btn-sm' disabled={approving} onClick={() => setApproveOpen(false)}>
              Cancel
            </button>
          </div>
        </div>
      )}

      {payOpen && (
        <div className='box-card mb-3' style={{ borderLeft: '4px solid #E6A23C' }}>
          <h6>Mark paid (offline)</h6>
          <p className='mb-1'>
            Mark order <strong>{order.orderSn || `#${order.id}`}</strong> ({money(order.actualPrice)}) as paid with an offline tender? This
            cannot be undone.
          </p>
          {isCj && (
            <p className='text-danger fw-bold mb-1'>
              This will also submit the order to CJ Dropshipping (live order placement).
            </p>
          )}
          <div className='d-flex align-items-center gap-2 mt-2'>
            <input
              className='form-control'
              style={{ maxWidth: 320 }}
              placeholder='Payment reference (optional)'
              value={payReference}
              onChange={e => setPayReference(e.target.value)}
            />
            <button className='btn btn-success btn-sm' disabled={paying} onClick={onConfirmMarkPaid}>
              {paying ? 'Marking paid…' : 'Confirm mark paid'}
            </button>
            <button
              className='btn btn-outline-secondary btn-sm'
              disabled={paying}
              onClick={() => {
                setPayOpen(false);
                setPayReference('');
              }}
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      <div className='row g-3'>
        <div className='col-md-6'>
          <div className='box-card'>
            <h6>Customer</h6>
            <div>{user?.nickname || order.userName || `#${order.userId ?? '—'}`}</div>
            {user?.mobile && <div className='text-muted small'>{user.mobile}</div>}
          </div>
        </div>
        <div className='col-md-6'>
          {isPickup ? (
            <div className='box-card'>
              <h6>In-store pickup</h6>
              <div>{pickupStore ?? '—'}</div>
              {(order.pickupName || order.pickupMobile) && (
                <div className='text-muted small'>
                  {order.pickupName}
                  {order.pickupName && order.pickupMobile && ' · '}
                  {order.pickupMobile}
                </div>
              )}
              {order.verifyCode && (
                <div className='small mt-1'>
                  Pickup code: <strong>{order.verifyCode}</strong>
                  {order.verifyTime ? (
                    <span className='text-muted'>
                      {' '}
                      — verified {order.verifyTime}
                      {order.verifiedBy ? ` by ${order.verifiedBy}` : ''}
                    </span>
                  ) : (
                    <span className='text-muted'> — not verified yet</span>
                  )}
                </div>
              )}
            </div>
          ) : (
            <div className='box-card'>
              <h6>Shipping</h6>
              <div>{order.consignee || '—'}</div>
              {order.mobile && <div className='text-muted small'>{order.mobile}</div>}
              {order.address && <div className='text-muted small'>{order.address}</div>}
              {order.shipChannel && (
                <div className='small mt-1'>
                  {order.shipChannel} · {order.shipSn}
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      <div className='box-card mt-3'>
        <h6>Items</h6>
        <table className='el-table'>
          <thead>
            <tr>
              <th>Item</th>
              <th>Spec</th>
              <th className='text-end'>Price</th>
              <th className='text-end'>Qty</th>
            </tr>
          </thead>
          <tbody>
            {goods.length === 0 ? (
              <tr>
                <td colSpan={4} className='text-center text-muted py-4'>
                  No items.
                </td>
              </tr>
            ) : (
              goods.map(g => (
                <tr key={g.id}>
                  <td>
                    <div className='d-flex align-items-center gap-2'>
                      {g.picUrl && <img src={g.picUrl} alt='' className='cell-thumb' />}
                      <span>{g.goodsName}</span>
                    </div>
                  </td>
                  <td className='text-muted small'>{(g.specifications ?? []).join(' / ') || '—'}</td>
                  <td className='text-end'>{money(g.price)}</td>
                  <td className='text-end'>{g.number ?? '—'}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className='box-card mt-3'>
        <h6>Totals</h6>
        <div className='d-flex justify-content-between'>
          <span className='text-muted'>Goods</span>
          <span>{money(order.orderPrice)}</span>
        </div>
        <div className='d-flex justify-content-between'>
          <span className='text-muted'>Freight</span>
          <span>{money(order.freightPrice)}</span>
        </div>
        <div className='d-flex justify-content-between fw-bold mt-1'>
          <span>Paid</span>
          <span>{money(order.actualPrice)}</span>
        </div>
        {order.payTime && <div className='text-muted small mt-1'>Paid at {order.payTime}</div>}
      </div>

      {/* Tracking applies to CJ orders and any shipped order (Wave 4 widened
          local tracking); pickup orders have nothing to track. */}
      {id && !isPickup && <TrackingPanel orderId={id} shipChannel={order.shipChannel} shipSn={order.shipSn} />}
    </div>
  );
};

export default OrderDetail;
