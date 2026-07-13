import * as React from 'react';
import ConfigGroupForm from './ConfigGroupForm';

// Express / freight settings (litemall_express_*: freight_min, freight_value)
// — /srv/private/admin/config/express.
const ConfigExpress: React.FC = () => <ConfigGroupForm group='express' title='Express settings' />;

export default ConfigExpress;
