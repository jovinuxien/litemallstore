import {
  ConfigGroup,
  ConfigMap,
  useGetConfigGroupQuery,
  useUpdateConfigGroupMutation,
} from 'app/shared/reducers/private/services/adminParityApi';
import { errnoMessage } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';

// Shared editor for one system-config group (mall / express / order), served
// by this gateway at GET/POST /srv/private/admin/config/{group} as a flat
// {key: value} string map. Renders one labeled input per key (label = key with
// the litemall_<group>_ prefix stripped and prettified) and posts the full map
// back on Save. A misspelled / foreign key comes back errno 402 with an errmsg
// naming the key — surfaced inline. Live-verified shapes (2026-07-13).

interface Props {
  group: ConfigGroup;
  title: string;
}

const prettify = (key: string, group: ConfigGroup): string => {
  const stripped = key.replace(`litemall_${group}_`, '').replace(/_/g, ' ');
  return stripped.charAt(0).toUpperCase() + stripped.slice(1);
};

const ConfigGroupForm: React.FC<Props> = ({ group, title }) => {
  const { data, isLoading, isError, error } = useGetConfigGroupQuery(group);
  const [updateConfig, { isLoading: saving }] = useUpdateConfigGroupMutation();

  const [values, setValues] = React.useState<ConfigMap>({});
  const [saveError, setSaveError] = React.useState<string | null>(null);
  const [saved, setSaved] = React.useState(false);

  React.useEffect(() => {
    if (data) setValues(data);
  }, [data]);

  const errStatus = (error as { status?: number | string })?.status;
  const keys = Object.keys(data ?? {}).sort();

  const set = (key: string, value: string) => {
    setSaved(false);
    setValues(prev => ({ ...prev, [key]: value }));
  };

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaveError(null);
    setSaved(false);
    const res = await updateConfig({ group, values });
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) {
      setSaveError(msg);
      return;
    }
    setSaved(true);
  };

  if (isLoading) {
    return (
      <div className='app-container text-center p-5'>
        <span className='spinner-border text-primary' role='status' />
      </div>
    );
  }

  return (
    <div className='app-container'>
      <h5 className='mb-3'>{title}</h5>

      {isError && <div className='alert alert-danger'>Failed to load settings{errStatus ? ` (${errStatus})` : ''}.</div>}
      {saveError && <div className='alert alert-danger'>{saveError}</div>}
      {saved && <div className='alert alert-success'>Settings saved.</div>}

      {keys.length === 0 && !isError ? (
        <div className='text-muted py-4'>No settings found for this group.</div>
      ) : (
        <form onSubmit={onSubmit} style={{ maxWidth: 640 }}>
          {keys.map(key => (
            <div className='mb-3' key={key}>
              <label className='form-label' htmlFor={`cfg-${key}`}>
                {prettify(key, group)}
              </label>
              <input id={`cfg-${key}`} className='form-control' value={values[key] ?? ''} onChange={e => set(key, e.target.value)} />
              <div className='form-text text-muted'>{key}</div>
            </div>
          ))}
          <div className='mt-3'>
            <button className='btn btn-primary' type='submit' disabled={saving}>
              {saving ? 'Saving…' : 'Save'}
            </button>
          </div>
          <div className='form-text text-muted mt-3'>Changes may require a service restart to take effect.</div>
        </form>
      )}
    </div>
  );
};

export default ConfigGroupForm;
