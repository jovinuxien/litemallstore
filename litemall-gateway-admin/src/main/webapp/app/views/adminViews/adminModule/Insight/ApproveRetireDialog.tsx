import { useApproveRetireCandidatesMutation } from 'app/shared/reducers/private/services/insightApi';
import { errnoMessage } from 'app/views/adminViews/adminModule/_shared/crudUi';
import { nextWednesday } from 'app/views/adminViews/adminModule/Insight/insightFormat';
import * as React from 'react';
import { Button, Modal } from 'react-bootstrap';

// Wave 14: approve a batch of retirement candidates
// (POST /insight/retire-candidates/approve {goodsIds[], executeOn}). The
// nightly 02:00 executor flips approved batches whose executeOn has arrived
// to OFF-SALE — reversible via the goods panel's on-sale toggle, and off-sale
// goods stay viewable but unbuyable. Default date = next scheduled Wednesday.

interface Props {
  goodsIds: number[];
  onClose: () => void;
}

const ApproveRetireDialog: React.FC<Props> = ({ goodsIds, onClose }) => {
  const [approve, { isLoading: saving }] = useApproveRetireCandidatesMutation();

  const [executeOn, setExecuteOn] = React.useState<string>(nextWednesday());
  const [error, setError] = React.useState<string | null>(null);
  const [done, setDone] = React.useState(false);

  const onSubmit = async () => {
    if (!executeOn) {
      setError('Pick the execution date.');
      return;
    }
    setError(null);
    const res = await approve({ goodsIds, executeOn });
    if ('error' in res && res.error) {
      const status = (res.error as { status?: number | string })?.status;
      setError(`Request failed${status ? ` (${status})` : ''}.`);
      return;
    }
    const msg = errnoMessage(res.data);
    if (msg) {
      setError(msg);
      return;
    }
    setDone(true);
  };

  return (
    <Modal show onHide={onClose} centered>
      <Modal.Header closeButton>
        <Modal.Title as='h5'>Approve for retirement — {goodsIds.length} goods</Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {done ? (
          <div className='alert alert-success mb-0'>
            {goodsIds.length} goods approved for retirement on {executeOn}. They move to the Approved tab and go off-sale when the nightly
            executor reaches that date.
          </div>
        ) : (
          <>
            {error && <div className='alert alert-danger'>{error}</div>}
            <div className='mb-2'>
              <label className='form-label'>Execute on</label>
              <input type='date' className='form-control' value={executeOn} onChange={e => setExecuteOn(e.target.value)} />
              <div className='form-text'>Defaults to the next scheduled Wednesday. On that date the batch goes OFF-SALE — products stay viewable but unbuyable, and leave search, deals and the sitemap. Reversible via the goods panel&rsquo;s on-sale toggle.</div>
            </div>
          </>
        )}
      </Modal.Body>
      <Modal.Footer>
        <Button variant='secondary' onClick={onClose}>
          {done ? 'Close' : 'Cancel'}
        </Button>
        {!done && (
          <Button variant='primary' disabled={saving} onClick={onSubmit}>
            {saving ? 'Approving…' : `Approve ${goodsIds.length} goods`}
          </Button>
        )}
      </Modal.Footer>
    </Modal>
  );
};

export default ApproveRetireDialog;
