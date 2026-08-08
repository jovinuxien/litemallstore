import { IPostizChannel } from 'app/shared/reducers/private/services/postizApi';
import { Spinner } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';

// Postiz channel checkboxes — shared by the Products and DIY-page compose
// flows. Dynamic list from GET /postiz/channels; supported:false rows render
// disabled with the backend's reason.

export const channelLabel = (channels: IPostizChannel[], integrationId: string): string => {
  const c = channels.find(x => x.integrationId === integrationId);
  return c ? c.name || c.identifier || integrationId : integrationId;
};

interface Props {
  channels: IPostizChannel[];
  loading: boolean;
  error: boolean;
  selected: Set<string>;
  onToggle: (integrationId: string) => void;
}

const ChannelPicker: React.FC<Props> = ({ channels, loading, error, selected, onToggle }) => {
  if (error) return <div className='alert alert-danger'>Failed to load Postiz channels.</div>;
  if (loading) return <Spinner />;
  if (channels.length === 0) return <div className='text-muted'>No channels connected in Postiz yet.</div>;
  return (
    <div className='d-flex flex-wrap gap-3 mb-2'>
      {channels.map(c => {
        const unsupported = c.supported === false;
        return (
          <label key={c.integrationId} className={`form-check d-flex align-items-center gap-2 border rounded px-3 py-2${unsupported ? ' text-muted' : ''}`}>
            <input
              type='checkbox'
              className='form-check-input'
              checked={selected.has(c.integrationId)}
              disabled={unsupported}
              onChange={() => onToggle(c.integrationId)}
            />
            {c.picture && <img src={c.picture} alt='' style={{ width: 24, height: 24, borderRadius: '50%' }} />}
            <span>
              {c.name || c.identifier || c.integrationId}
              {c.identifier && <span className='text-muted small ms-1'>({c.identifier})</span>}
              {unsupported && <span className='small d-block'>not supported{c.reason ? ` — ${c.reason}` : ''}</span>}
            </span>
          </label>
        );
      })}
    </div>
  );
};

export default ChannelPicker;
