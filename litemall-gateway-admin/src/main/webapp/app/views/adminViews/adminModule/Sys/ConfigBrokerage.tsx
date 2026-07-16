import ConfigGroupForm from './ConfigGroupForm';
import * as React from 'react';

// Brokerage (affiliate commission) settings — the four litemall_brokerage_*
// rows seeded by order's Wave-5 migration: enabled / rate (%) / freeze-days /
// min-extract. Served by the edge at /srv/private/admin/config/brokerage with
// the same unknown-key guard as the other groups. NB: order's BrokerageService
// reads these per paid-order event, so a rate change affects the NEXT
// commission without a restart (unlike the static-cached groups).
const ConfigBrokerage: React.FC = () => <ConfigGroupForm group='brokerage' title='Brokerage settings' />;

export default ConfigBrokerage;
