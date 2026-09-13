import React, { useEffect, useState } from 'react';
import { Spinner } from 'react-bootstrap';

import { Cell, CellGroup } from 'app/components/commonComponents/storefront';
import { orderApi } from 'app/shared/api';
import { useTranslation } from 'app/i18n';
import { IOrderTimelineEntry } from 'app/shared/model/order/order.model';
import { fmtDateTime } from 'app/shared/util/dateTime';

import { describeTimelineEntry } from './timelineCopy';

/**
 * The order's status timeline (`GET /srv/order/{id}/timeline`, oldest first) as plain
 * sentences — the "status timeline" the help centre has promised since Wave 9.1 and the
 * SPA never rendered (order lifecycle contract §4). errno 404 (foreign/unknown order)
 * hides the section; any other failure shows an unavailable note; no entries ⇒ nothing,
 * never an empty heading.
 */
const OrderTimelinePanel: React.FC<{ orderId: number | string }> = ({ orderId }) => {
  const { t } = useTranslation('order');
  const [entries, setEntries] = useState<IOrderTimelineEntry[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [hidden, setHidden] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    orderApi
      .timeline(orderId)
      .then(list => {
        if (!cancelled) setEntries(Array.isArray(list) ? list : []);
      })
      .catch(e => {
        if (cancelled) return;
        if ((e as { errno?: number })?.errno === 404) setHidden(true);
        else setEntries(null);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [orderId]);

  if (hidden) return null;
  if (!loading && entries && entries.length === 0) return null;

  return (
    <CellGroup title={t('timeline.title')}>
      {loading ? (
        <div className='p-3 text-center text-muted'>
          <Spinner animation='border' size='sm' className='me-2' />
          {t('timeline.fetching')}
        </div>
      ) : !entries ? (
        <Cell title={<span className='text-muted'>{t('timeline.unavailable')}</span>} />
      ) : (
        <div className='px-3 pb-3'>
          <ol className='list-unstyled mb-0 mt-2 lm-timeline'>
            {entries.map((entry, i) => {
              const line = describeTimelineEntry(entry);
              const latest = i === entries.length - 1;
              const marker =
                line.tone === 'warn' ? 'bi-exclamation-circle-fill text-warning' : latest ? 'bi-record-circle text-lm-primary' : 'bi-check-circle text-muted';
              return (
                <li key={`${entry.changeType ?? 'step'}-${i}`} className='d-flex align-items-start mb-2' data-tone={line.tone}>
                  <i className={`bi ${marker} me-2 small`} />
                  <div>
                    <div className={latest ? 'fw-semibold' : ''}>{line.headline}</div>
                    {line.detail && <div className='small'>{line.detail}</div>}
                    <div className='small text-muted'>
                      {fmtDateTime(entry.changeTime)}
                      {line.who ? ` · ${line.who}` : ''}
                    </div>
                  </div>
                </li>
              );
            })}
          </ol>
        </div>
      )}
    </CellGroup>
  );
};

export default OrderTimelinePanel;
