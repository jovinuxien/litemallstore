/**
 * Plain-text projection of a goods `brief`. CJ imports store a raw supplier
 * HTML blob in that column (typically `<p><img …/></p>`), which React would
 * escape into literal "<p><img…" text on the PDP. Tags are dropped, text
 * content kept; an image-only brief collapses to the empty string.
 */
export const briefToText = (raw?: string | null): string => {
  if (!raw) return '';
  if (!raw.includes('<')) return raw.trim();
  try {
    const doc = new DOMParser().parseFromString(raw, 'text/html');
    return (doc.body.textContent ?? '').replace(/\s+/g, ' ').trim();
  } catch {
    return '';
  }
};
