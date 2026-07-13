import * as React from 'react';
import ConfigGroupForm from './ConfigGroupForm';

// Mall settings (litemall_mall_*: name, address, phone, qq, longitude,
// latitude) — /srv/private/admin/config/mall.
const ConfigMall: React.FC = () => <ConfigGroupForm group='mall' title='Mall settings' />;

export default ConfigMall;
