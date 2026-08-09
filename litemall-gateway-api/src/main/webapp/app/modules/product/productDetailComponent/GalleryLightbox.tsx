import React, { useCallback, useEffect } from 'react';

/**
 * Fullscreen gallery viewer (Amazon's click-to-zoom equivalent, no deps):
 * backdrop overlay with the current image, prev/next cycling, a thumbnail
 * strip, and close on ×, backdrop click, or Escape.
 */
interface Props {
  images: string[];
  current: string;
  onSelect: (img: string) => void;
  onClose: () => void;
}

const GalleryLightbox: React.FC<Props> = ({ images, current, onSelect, onClose }) => {
  const idx = Math.max(0, images.indexOf(current));
  const step = useCallback(
    (delta: number) => {
      if (images.length < 2) return;
      onSelect(images[(idx + delta + images.length) % images.length]);
    },
    [images, idx, onSelect]
  );

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
      else if (e.key === 'ArrowLeft') step(-1);
      else if (e.key === 'ArrowRight') step(1);
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose, step]);

  if (!images.length) return null;

  return (
    <div className='lm-pdp__lightbox' role='dialog' aria-modal='true' aria-label='Product images' onClick={onClose}>
      <div className='lm-pdp__lightbox-body' onClick={e => e.stopPropagation()}>
        <button type='button' className='lm-pdp__lightbox-close' aria-label='Close' onClick={onClose}>
          <i className='bi bi-x-lg' />
        </button>
        {images.length > 1 && (
          <button type='button' className='lm-pdp__lightbox-nav prev' aria-label='Previous image' onClick={() => step(-1)}>
            <i className='bi bi-chevron-left' />
          </button>
        )}
        <img src={images[idx]} alt='' />
        {images.length > 1 && (
          <button type='button' className='lm-pdp__lightbox-nav next' aria-label='Next image' onClick={() => step(1)}>
            <i className='bi bi-chevron-right' />
          </button>
        )}
        {images.length > 1 && (
          <div className='lm-pdp__lightbox-thumbs'>
            {images.map(img => (
              <button
                key={img}
                type='button'
                className={img === images[idx] ? 'is-active' : ''}
                onClick={() => onSelect(img)}
              >
                <img src={img} alt='' />
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default GalleryLightbox;
