import { describe, expect, it } from 'vitest';
import { parseHighlight } from './highlight.ts';

describe('parseHighlight', () => {
  it('keeps an HTML-encoded payload as text and marks the match', () => {
    // Real Elasticsearch html-encoder output for the title `<img src=x onerror=alert(1)> buy milk`.
    expect(parseHighlight('&lt;img src=x onerror=alert(1)&gt; buy <em>milk</em>')).toEqual([
      { text: '<img src=x onerror=alert(1)> buy ', match: false },
      { text: 'milk', match: true },
    ]);
  });

  it('decodes every entity the html encoder emits', () => {
    expect(parseHighlight('a &amp; b &quot;c&quot; &#x27;d&#x27; e&#x2F;f')).toEqual([
      { text: `a & b "c" 'd' e/f`, match: false },
    ]);
  });

  it('decodes only one level, so escaped entities stay literal', () => {
    expect(parseHighlight('&amp;lt;b&amp;gt;')).toEqual([{ text: '&lt;b&gt;', match: false }]);
  });

  it('treats unencoded tags as text if the server ever stops encoding', () => {
    expect(parseHighlight('<img src=x onerror=alert(1)><em>x</em><script>1</script>')).toEqual([
      { text: '<img src=x onerror=alert(1)>', match: false },
      { text: 'x', match: true },
      { text: '<script>1</script>', match: false },
    ]);
  });

  it('only treats a bare <em> as a match marker', () => {
    expect(parseHighlight('<em onclick=x>y</em>')).toEqual([{ text: '<em onclick=x>y', match: false }]);
  });

  it('handles multiple matches', () => {
    expect(parseHighlight('<em>a</em> and <em>b</em>')).toEqual([
      { text: 'a', match: true },
      { text: ' and ', match: false },
      { text: 'b', match: true },
    ]);
  });
});
