export interface HighlightSegment {
  text: string;
  match: boolean;
}

// Entities Elasticsearch's "html" highlight encoder emits.
const ENTITIES: Record<string, string> = {
  '&amp;': '&',
  '&lt;': '<',
  '&gt;': '>',
  '&quot;': '"',
  '&#x27;': "'",
  '&#39;': "'",
  '&#x2F;': '/',
  '&#47;': '/',
};

function decodeEntities(text: string): string {
  return text.replace(/&(?:amp|lt|gt|quot|#x27|#39|#x2F|#47);/g, (entity) => ENTITIES[entity]);
}

/**
 * Splits an Elasticsearch highlight fragment into plain-text segments, treating only the {@code <em>}
 * match markers as markup (threat #3). Everything else is decoded to text and rendered by React as text,
 * never as HTML, so the result is safe even if the server stops HTML-encoding fragments.
 */
export function parseHighlight(fragment: string): HighlightSegment[] {
  const segments: HighlightSegment[] = [];
  let match = false;
  for (const part of fragment.split(/(<em>|<\/em>)/)) {
    if (part === '<em>') {
      match = true;
    } else if (part === '</em>') {
      match = false;
    } else if (part) {
      segments.push({ text: decodeEntities(part), match });
    }
  }
  return segments;
}
