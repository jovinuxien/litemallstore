import React from 'react';

/**
 * Renders an OCS highlight snippet — a plain string with matches wrapped in
 * `<em>` (or `<mark>`) per the Wave-9 search contract — as React nodes WITHOUT
 * ever injecting raw HTML. The string is tokenized on well-formed
 * `<em>…</em>` / `<mark>…</mark>` pairs; those become real React elements, and
 * every other character (including any other tag, e.g. an injected
 * `<script>…</script>`) stays a text node that React escapes on render. There
 * is no dangerouslySetInnerHTML anywhere on this path, so a hostile snippet is
 * inert by construction.
 */

const TAG_RE = /<(em|mark)>(.*?)<\/\1>/gis;

// The engine may entity-escape the non-match text around the tags; decode the
// few standard entities so titles show "&" not "&amp;". The decoded value is
// only ever rendered as a TEXT node, so decoding "&lt;script&gt;" is safe —
// it displays as literal "<script>" without becoming markup.
const ENTITY_RE = /&(amp|lt|gt|quot|#0*39|apos|nbsp);/g;
const ENTITIES: Record<string, string> = {
  amp: '&',
  lt: '<',
  gt: '>',
  quot: '"',
  '#39': "'",
  apos: "'",
  nbsp: ' ',
};
const decodeEntities = (s: string): string =>
  s.replace(ENTITY_RE, (whole, name: string) => ENTITIES[name.replace(/^#0+/, '#')] ?? whole);

export const renderSnippet = (snippet: string): React.ReactNode => {
  const nodes: React.ReactNode[] = [];
  let last = 0;
  let key = 0;
  TAG_RE.lastIndex = 0;
  for (let m = TAG_RE.exec(snippet); m != null; m = TAG_RE.exec(snippet)) {
    if (m.index > last) nodes.push(decodeEntities(snippet.slice(last, m.index)));
    const Tag = m[1].toLowerCase() as 'em' | 'mark';
    nodes.push(<Tag key={key++}>{decodeEntities(m[2])}</Tag>);
    last = m.index + m[0].length;
  }
  if (last < snippet.length) nodes.push(decodeEntities(snippet.slice(last)));
  return <>{nodes}</>;
};

export default renderSnippet;
