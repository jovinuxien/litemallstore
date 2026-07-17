import React from 'react';

/**
 * The "this is not legal copy yet" banner (Wave-7 Task D).
 *
 * <p>The Wave-7 plan is explicit that legal copy is a counsel deliverable and that
 * shipping invented legal text is its own risk. So these pages ship as real, routed,
 * linked structure — the engineering half — with the operative wording marked
 * unmistakably as pending. That is deliberately louder than a subtle note: a
 * placeholder that reads like a policy is worse than no page, because a customer (or
 * a regulator) would reasonably rely on it.
 *
 * <p>Section headings and the factual descriptions of what the system actually does
 * (which processors run, what data moves where) are ours to write and are accurate.
 * The legal commitments around them are not. See
 * litemall-gateway-api/docs/LAUNCH-BLOCKER-legal-copy.md.
 *
 * <p><b>Delete this component when counsel-reviewed copy lands</b> — its presence
 * anywhere in the bundle means the store is not launch-ready.
 */
const LegalPlaceholder: React.FC<{ page: string }> = ({ page }) => (
  <div className='alert alert-warning border-warning d-flex gap-3' role='alert'>
    <i className='bi bi-exclamation-triangle-fill fs-4 flex-shrink-0' />
    <div>
      <div className='fw-semibold'>Draft — not legally binding</div>
      <div className='small'>
        This {page} has not been reviewed by legal counsel and must not be relied on. The structure and the
        descriptions of how the site works are accurate; the binding wording is outstanding and is required
        before launch.
      </div>
    </div>
  </div>
);

export default LegalPlaceholder;
