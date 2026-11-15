import { ISeasonRule, useGetSeasonRulesQuery, useUpdateSeasonRuleMutation } from 'app/shared/reducers/private/services/insightApi';
import { errnoMessage, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import { fmtMoney } from 'app/views/adminViews/adminModule/Insight/insightFormat';
import { splitTerms } from 'app/views/adminViews/adminModule/Insight/seasonFormat';
import SeasonRuleForm from 'app/views/adminViews/adminModule/Insight/SeasonRuleForm';
import * as React from 'react';
import { Link } from 'react-router-dom';

// Season rules (/srv/private/admin/insight/season-rules): one row per season.
// Rules are data — terms, window, price band, weights and the per-season
// kill-switch — and every edit takes effect at the next scoring run.

const SeasonRuleList: React.FC = () => {
  const { data, isLoading, isFetching, isError, error } = useGetSeasonRulesQuery();
  const [update, { isLoading: toggling }] = useUpdateSeasonRuleMutation();
  const [editing, setEditing] = React.useState<string | null>(null);
  const [actionError, setActionError] = React.useState<string | null>(null);

  const rules = data ?? [];
  const errStatus = (error as { status?: number | string })?.status;
  const editingRule = rules.find(r => r.seasonKey === editing);

  const onToggle = async (rule: ISeasonRule) => {
    setActionError(null);
    const res = await update({ key: rule.seasonKey, patch: { enabled: rule.enabled === false } });
    if ('error' in res && res.error) {
      const s = (res.error as { status?: number | string })?.status;
      setActionError(`${rule.seasonKey}: request failed${s ? ` (${s})` : ''}. Nothing was changed.`);
      return;
    }
    const msg = errnoMessage((res as { data?: unknown }).data);
    if (msg) setActionError(`${rule.seasonKey}: ${msg}`);
  };

  return (
    <div className='app-container'>
      <div className='filter-container'>
        <h4 className='mb-0 filter-item'>Season rules</h4>
        <Link className='btn btn-outline-secondary filter-item' to='/admin/goods/season-candidates'>
          Season candidates
        </Link>
        {isFetching && <Spinner />}
      </div>
      <p className='text-muted small'>
        Each season is described by its match terms, date window, price band and weights. Changes apply at the next scoring run (04:35 nightly,
        or Run scorer now on the candidates page).
      </p>

      {isError && <div className='alert alert-danger'>Failed to load season rules{errStatus ? ` (${errStatus})` : ''}.</div>}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>Season</th>
            <th>Window</th>
            <th className='text-end'>Match terms</th>
            <th className='text-end'>Exclude terms</th>
            <th>Price band</th>
            <th>Status</th>
            <th className='text-end'>Actions</th>
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={7} className='text-center p-5'>
                <span className='spinner-border text-primary' role='status' />
              </td>
            </tr>
          ) : isError ? null : rules.length === 0 ? (
            <tr>
              <td colSpan={7} className='text-center text-muted py-5'>
                No season rules exist yet.
              </td>
            </tr>
          ) : (
            rules.map(rule => {
              const terms = splitTerms(rule.terms);
              const on = rule.enabled !== false;
              return (
                <tr key={rule.seasonKey}>
                  <td>
                    {rule.name || rule.seasonKey} <span className='text-muted small'>({rule.seasonKey})</span>
                  </td>
                  <td>
                    {rule.windowStartMd || '—'} to {rule.windowEndMd || '—'}
                  </td>
                  <td className='text-end' title={terms.match.join(', ')}>
                    {terms.match.length}
                  </td>
                  <td className='text-end' title={terms.exclude.join(', ')}>
                    {terms.exclude.length}
                  </td>
                  <td>
                    {rule.priceMin == null && rule.priceMax == null
                      ? 'Any price'
                      : `${rule.priceMin == null ? 'any' : fmtMoney(rule.priceMin)} – ${rule.priceMax == null ? 'any' : fmtMoney(rule.priceMax)}`}
                  </td>
                  <td>
                    <Tag tag={on ? 'success' : 'info'}>{on ? 'Enabled' : 'Off'}</Tag>
                  </td>
                  <td className='text-end'>
                    <button className='btn btn-sm btn-outline-primary me-1' onClick={() => setEditing(rule.seasonKey)}>
                      Edit
                    </button>
                    <button className='btn btn-sm btn-outline-secondary' disabled={toggling} onClick={() => onToggle(rule)}>
                      {on ? 'Switch off' : 'Switch on'}
                    </button>
                  </td>
                </tr>
              );
            })
          )}
        </tbody>
      </table>

      {editingRule && <SeasonRuleForm key={editingRule.seasonKey} rule={editingRule} onClose={() => setEditing(null)} />}
    </div>
  );
};

export default SeasonRuleList;
