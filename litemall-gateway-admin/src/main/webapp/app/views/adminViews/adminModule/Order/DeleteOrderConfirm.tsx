import { deleteConfirmMatches, orderDeleteTier } from 'app/shared/model/admin/order.model';
import { orderOpMessage, useDeleteOrderMutation } from 'app/shared/reducers/private/services/adminOrderCjApi';
import { money } from 'app/shared/util/money';
import * as React from 'react';

// Inline confirm for the admin order delete, shared by the order list and the
// order detail page so the two can never word the consequences differently.
// The delete is SOFT (the row stays in the database) and moves no money; a
// paid order must have its number typed back. Refusals are the server's words.

export interface DeletableOrder {
  id?: number;
  orderSn?: string;
  orderStatus?: number;
  cjOrderId?: string | null;
  actualPrice?: number | string;
}

interface Props {
  order: DeletableOrder;
  onDeleted: (message: string) => void;
  onCancel: () => void;
}

const DeleteOrderConfirm: React.FC<Props> = ({ order, onDeleted, onCancel }) => {
  const [deleteOrder, { isLoading: deleting }] = useDeleteOrderMutation();
  const [typed, setTyped] = React.useState('');
  const [error, setError] = React.useState<string | null>(null);

  const tier = orderDeleteTier(order);
  const label = order.orderSn || `#${order.id}`;
  const paid = tier === 'paid';
  const ready = !paid || deleteConfirmMatches(order.orderSn, typed);

  const onConfirm = async () => {
    if (order.id == null || !ready) return;
    setError(null);
    const res = await deleteOrder({ orderId: order.id, confirmOrderSn: paid ? typed.trim() : undefined });
    const msg = orderOpMessage(res);
    if (msg) {
      setError(msg);
      return;
    }
    onDeleted(`Order ${label} deleted.`);
  };

  return (
    <div className='box-card mb-3' style={{ borderLeft: '4px solid #F56C6C' }} data-testid='delete-order-confirm'>
      <h6>Delete order</h6>
      <p className='mb-1'>
        Delete order <strong>{label}</strong> ({money(order.actualPrice)})? It disappears from this panel and from the customer&apos;s account.
        The record stays in the database; there is no restore button.
      </p>
      {paid && (
        <>
          <p className='text-danger fw-bold mb-1'>
            This order is paid and was never sent to CJ. Deleting it does not refund the customer and does not return the stock — use it for
            test purchases only.
          </p>
          <label className='form-label mb-1' htmlFor='delete-order-sn'>
            Type the order number <strong>{order.orderSn}</strong> to confirm
          </label>
          <input
            id='delete-order-sn'
            className='form-control'
            style={{ maxWidth: 260 }}
            value={typed}
            autoComplete='off'
            onChange={e => setTyped(e.target.value)}
          />
        </>
      )}
      {error && <div className='alert alert-danger mt-2 mb-0'>{error}</div>}
      <div className='d-flex align-items-center gap-2 mt-2'>
        <button type='button' className='btn btn-danger btn-sm' disabled={deleting || !ready} onClick={onConfirm}>
          {deleting ? 'Deleting…' : 'Confirm delete'}
        </button>
        <button type='button' className='btn btn-outline-secondary btn-sm' disabled={deleting} onClick={onCancel}>
          Cancel
        </button>
      </div>
    </div>
  );
};

export default DeleteOrderConfirm;
