import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { Highlighted } from './Highlighted.tsx';

// Threat #3: user-controlled titles reach the admin search view; they must never become markup.
describe('Highlighted', () => {
  const payload = '<img src=x onerror=alert(1)>';

  it('renders the fallback title as escaped text', () => {
    const html = renderToStaticMarkup(<Highlighted fallback={payload} />);
    expect(html).toBe('<span>&lt;img src=x onerror=alert(1)&gt;</span>');
    expect(html).not.toContain('<img');
  });

  it('renders a highlight fragment with only the match as markup', () => {
    const html = renderToStaticMarkup(
      <Highlighted fragment="&lt;img src=x onerror=alert(1)&gt; buy <em>milk</em>" fallback="unused" />,
    );
    expect(html).toBe('<span>&lt;img src=x onerror=alert(1)&gt; buy <em>milk</em></span>');
  });

  it('escapes raw tags in a fragment the server failed to encode', () => {
    const html = renderToStaticMarkup(<Highlighted fragment={`${payload}<em>x</em>`} fallback="unused" />);
    expect(html).not.toContain('<img');
    expect(html).toContain('&lt;img');
  });
});
