import { Fragment } from 'react';
import { parseHighlight } from './highlight.ts';

/** A search hit's first highlight fragment with matches emphasised, or the plain title if there is none. */
export function Highlighted({ fragment, fallback }: { fragment?: string; fallback: string }) {
  if (!fragment) {
    return <span>{fallback}</span>;
  }
  return (
    <span>
      {parseHighlight(fragment).map((segment, i) =>
        segment.match ? <em key={i}>{segment.text}</em> : <Fragment key={i}>{segment.text}</Fragment>,
      )}
    </span>
  );
}
