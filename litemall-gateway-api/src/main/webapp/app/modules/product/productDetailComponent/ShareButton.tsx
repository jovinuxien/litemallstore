import React, { useState } from 'react';

import { productPath } from 'app/shared/util/slug';

/**
 * Share the canonical slugged product URL (Wave-13 slug rules): the native
 * share sheet where the browser has one (mobile), copy-to-clipboard fallback
 * elsewhere. Fail-silent — share/clipboard rejections (incl. user cancel)
 * just leave the button as-is.
 */
interface Props {
  goodsId?: number | string;
  name?: string;
}

const ShareButton: React.FC<Props> = ({ goodsId, name }) => {
  const [copied, setCopied] = useState(false);
  if (goodsId == null) return null;

  const share = async () => {
    const url = `${window.location.origin}${productPath(goodsId!, name)}`;
    try {
      if (navigator.share) {
        await navigator.share({ title: name || 'Trovemo', url });
        return;
      }
      await navigator.clipboard.writeText(url);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      /* user cancelled the sheet or clipboard denied — nothing to do */
    }
  };

  return (
    <button type='button' className='lm-pdp__share' onClick={share}>
      <i className={`bi ${copied ? 'bi-check-lg' : 'bi-share'} me-1`} />
      {copied ? 'Link copied' : 'Share'}
    </button>
  );
};

export default ShareButton;
