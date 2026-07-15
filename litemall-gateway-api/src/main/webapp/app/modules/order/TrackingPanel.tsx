import React, { useEffect, useState } from 'react';
import { Spinner } from 'react-bootstrap';

import { Cell, CellGroup } from 'app/components/commonComponents/storefront';
import { orderApi } from 'app/shared/api';
import { ITracking } from 'app/shared/model/order/order.model';

/**
 * Customer shipment tracking (Wave 4 Task B — net-new view) over
 * `GET /srv/order/{id}/tracking` (owner-scoped; contract:
 * litemall-order/docs/handoff-gateway-admin-cj-tracking.md).
 *
 * States: loading (first call may hit live CJ trackInfo before the 1h server
 * cache warms), not-shipped ({shipped:false} — a normal state, not an error),
 * shipped-without-events ("details temporarily unavailable" — CJ unreachable or
 * a hand-shipped local order), and the carrier + event timeline. errno 404
 * (foreign/unknown order) hides the section entirely.
 */
const TrackingPanel: React.FC<{ orderId: number | string }> = ({ orderId }) => {
  const [tracking, setTracking] = useState<ITracking | null>(null);
  const [loading, setLoading] = useState(true);
  const [hidden, setHidden] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    orderApi
      .tracking(orderId)
      .then(t => {
        if (!cancelled) setTracking(t ?? null);
      })
      .catch(e => {
        // errno 404 (foreign/unknown order) → no section; other failures show
        // the unavailable note.
        if (!cancelled) {
          if ((e as { errno?: number })?.errno === 404) setHidden(true);
          else setTracking(null);
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [orderId]);

  if (hidden) return null;

  return (
    <CellGroup title='Shipment tracking'>
      {loading ? (
        <div className='p-3 text-center text-muted'>
          <Spinner animation='border' size='sm' className='me-2' />
          Fetching tracking…
        </div>
      ) : !tracking ? (
        <Cell title={<span className='text-muted'>Tracking is temporarily unavailable.</span>} />
      ) : !tracking.shipped ? (
        <Cell
          title={
            <span className='text-muted'>
              <i className='bi bi-box-seam me-2' />
              Not shipped yet — tracking appears here once your order ships.
            </span>
          }
        />
      ) : (
        <>
          {tracking.carrier && <Cell title='Carrier' value={tracking.carrier} />}
          {tracking.trackNumber && <Cell title='Tracking no.' value={tracking.trackNumber} />}
          {tracking.lastMileCarrier && (
            <Cell title='Last mile' value={`${tracking.lastMileCarrier}${tracking.lastTrackNumber ? ` (${tracking.lastTrackNumber})` : ''}`} />
          )}
          {(tracking.origin || tracking.destination) && (
            <Cell title='Route' value={[tracking.origin, tracking.destination].filter(Boolean).join(' → ')} />
          )}
          {tracking.deliveryDay && <Cell title='Est. delivery' value={`${tracking.deliveryDay} days`} />}
          {tracking.status && <Cell title='Status' value={tracking.status} />}
          {(tracking.events ?? []).length > 0 ? (
            <div className='px-3 pb-3'>
              <ul className='list-unstyled mb-0 mt-2'>
                {(tracking.events ?? []).map((ev, i) => (
                  <li key={i} className='d-flex align-items-start mb-2'>
                    <i className={`bi ${i === 0 ? 'bi-record-circle text-lm-primary' : 'bi-circle text-muted'} me-2 small`} />
                    <div>
                      <div className='small'>{ev.description ?? ev.status}</div>
                      {ev.time && <div className='small text-muted'>{ev.time}</div>}
                    </div>
                  </li>
                ))}
              </ul>
            </div>
          ) : (
            <Cell title={<span className='text-muted'>Tracking details are temporarily unavailable — check back later.</span>} />
          )}
        </>
      )}
    </CellGroup>
  );
};

export default TrackingPanel;
