import { useGetLinksQuery } from 'app/shared/reducers/private/services/affiliateApi';
import { QRCodeSVG } from 'qrcode.react';
import * as React from 'react';

// My links (Wave 5): the invite code + canonical share URLs from
// GET /srv/private/affiliate/links, with client-side QR (qrcode.react — no
// server round-trip) and clipboard copy buttons. The product link is a
// template with a goods-id placeholder the affiliate fills in.

const GOODS_PLACEHOLDER = /<goodsId>|\{goodsId\}|:goodsId/;

const CopyButton: React.FC<{ text?: string }> = ({ text }) => {
  const [copied, setCopied] = React.useState(false);
  const onCopy = async () => {
    if (!text) return;
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      /* clipboard unavailable (http origin) — user can select manually */
    }
  };
  return (
    <button type='button' className={`btn btn-sm ${copied ? 'btn-success' : 'btn-outline-secondary'}`} onClick={onCopy} disabled={!text}>
      {copied ? 'Copied!' : 'Copy'}
    </button>
  );
};

const LinkRow: React.FC<{ label: string; url?: string; qr?: boolean }> = ({ label, url, qr }) => (
  <div className='card mb-3'>
    <div className='card-body'>
      <div className='text-muted small mb-1'>{label}</div>
      <div className='d-flex align-items-center gap-2 flex-wrap'>
        <code className='flex-grow-1' style={{ wordBreak: 'break-all' }}>
          {url || '—'}
        </code>
        <CopyButton text={url} />
      </div>
      {qr && url && (
        <div className='mt-3'>
          <QRCodeSVG value={url} size={144} includeMargin />
        </div>
      )}
    </div>
  </div>
);

const AffiliateLinks: React.FC = () => {
  const { data, isLoading, isError } = useGetLinksQuery();
  const [goodsId, setGoodsId] = React.useState('');

  if (isLoading) {
    return (
      <div className='app-container text-center p-5'>
        <span className='spinner-border text-primary' role='status' />
      </div>
    );
  }

  const template = data?.productUrl || data?.productUrlTemplate;
  const productLink = template && goodsId.trim() ? template.replace(GOODS_PLACEHOLDER, goodsId.trim()) : undefined;

  return (
    <div className='app-container' style={{ maxWidth: 720 }}>
      <h5 className='mb-3'>My invite links</h5>
      {isError && <div className='alert alert-danger'>Failed to load your links. Please try again.</div>}

      <div className='card mb-3'>
        <div className='card-body d-flex align-items-center gap-2'>
          <div>
            <div className='text-muted small'>Invite code</div>
            <div className='fs-4 fw-semibold'>{data?.inviteCode || '—'}</div>
          </div>
          <div className='ms-auto'>
            <CopyButton text={data?.inviteCode} />
          </div>
        </div>
      </div>

      <LinkRow label='Registration link — new users who sign up through it are attributed to you' url={data?.registerUrl} qr />

      <div className='card mb-3'>
        <div className='card-body'>
          <div className='text-muted small mb-1'>Product share link</div>
          <div className='input-group mb-2' style={{ maxWidth: 320 }}>
            <span className='input-group-text'>Goods id</span>
            <input className='form-control' value={goodsId} onChange={e => setGoodsId(e.target.value)} placeholder='e.g. 1181000' />
          </div>
          {productLink ? (
            <>
              <div className='d-flex align-items-center gap-2 flex-wrap'>
                <code className='flex-grow-1' style={{ wordBreak: 'break-all' }}>
                  {productLink}
                </code>
                <CopyButton text={productLink} />
              </div>
              <div className='mt-3'>
                <QRCodeSVG value={productLink} size={144} includeMargin />
              </div>
            </>
          ) : (
            <div className='form-text text-muted'>{template ? 'Enter a goods id to build the link.' : 'No product link template available.'}</div>
          )}
        </div>
      </div>
    </div>
  );
};

export default AffiliateLinks;
