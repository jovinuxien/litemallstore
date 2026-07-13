import * as React from 'react';
import ConfigGroupForm from './ConfigGroupForm';

// Order timing settings (litemall_order_*: unpaid, unconfirm, comment) —
// /srv/private/admin/config/order.
const ConfigOrder: React.FC = () => <ConfigGroupForm group='order' title='Order settings' />;

export default ConfigOrder;
