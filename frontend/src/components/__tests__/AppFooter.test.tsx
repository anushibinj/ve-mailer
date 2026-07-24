import { describe, it, expect, afterEach, vi, beforeEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import AppFooter, { sanitizeHtml } from '../AppFooter';

// ---------------------------------------------------------------------------
// sanitizeHtml unit tests
// ---------------------------------------------------------------------------
describe('sanitizeHtml', () => {
  it('returns empty string for empty input', () => {
    expect(sanitizeHtml('')).toBe('');
  });

  it('renders a simple allowed tag with text content', () => {
    const result = sanitizeHtml('<div>Hello</div>');
    expect(result).toBe('<div>Hello</div>');
  });

  it('strips script tags and their entire content', () => {
    const result = sanitizeHtml('<div>Safe<script>alert("xss")</script></div>');
    expect(result).toBe('<div>Safe</div>');
  });

  it('strips iframe tags and their content', () => {
    const result = sanitizeHtml('<div>Text<iframe src="https://evil.com"></iframe></div>');
    expect(result).toBe('<div>Text</div>');
  });

  it('strips inline event-handler attributes', () => {
    const result = sanitizeHtml('<div onclick="alert(1)">Click</div>');
    expect(result).toBe('<div>Click</div>');
  });

  it('strips javascript: href values', () => {
    const result = sanitizeHtml('<a href="javascript:alert(1)">Link</a>');
    expect(result).not.toContain('javascript:');
  });

  it('preserves a valid https href on anchor tags', () => {
    const result = sanitizeHtml('<a href="https://example.com">Link</a>');
    expect(result).toContain('href="https://example.com"');
    expect(result).toContain('Link');
  });

  it('strips disallowed wrapper tags but preserves their text content', () => {
    const result = sanitizeHtml('<article>Content</article>');
    expect(result).toBe('Content');
  });

  it('preserves class attribute on allowed elements', () => {
    const result = sanitizeHtml('<div class="custom-footer">Text</div>');
    expect(result).toContain('class="custom-footer"');
    expect(result).toContain('Text');
  });

  it('preserves style attribute on allowed elements', () => {
    const result = sanitizeHtml('<span style="font-weight:bold">Bold</span>');
    expect(result).toContain('style=');
    expect(result).toContain('Bold');
  });
});

// ---------------------------------------------------------------------------
// AppFooter component tests
// ---------------------------------------------------------------------------
describe('AppFooter', () => {
  beforeEach(() => {
    // Mock fetch so the /api/about call in useEffect never causes network errors in tests.
    // The promise resolves immediately so act() can flush all state updates.
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      json: () => Promise.resolve({ buildDate: 'development', buildTime: 'development', version: 'development' }),
    }));
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
  });

  it('always renders a footer element, even without VITE_FOOTER_HTML', async () => {
    // The footer always shows build metadata regardless of VITE_FOOTER_HTML
    vi.stubEnv('VITE_FOOTER_HTML', '');
    await act(async () => { render(<AppFooter />); });
    expect(screen.getByRole('contentinfo')).toBeInTheDocument();
  });

  it('renders a footer element when HTML content is provided', async () => {
    vi.stubEnv('VITE_FOOTER_HTML', '<div>Powered by VE Mailer</div>');
    await act(async () => { render(<AppFooter />); });
    expect(screen.getByRole('contentinfo')).toBeInTheDocument();
  });

  it('carries stacking and layout classes needed for the app-shell sticky footer', async () => {
    // z-10 keeps the footer above scrollable content; shrink-0 prevents the
    // flex container from compressing the footer; w-full ensures it spans the viewport.
    vi.stubEnv('VITE_FOOTER_HTML', '<div>Footer</div>');
    await act(async () => { render(<AppFooter />); });
    const footer = screen.getByRole('contentinfo');
    expect(footer.className).toContain('z-10');
    expect(footer.className).toContain('shrink-0');
    expect(footer.className).toContain('w-full');
  });

  it('renders a footer element in the DOM even when VITE_FOOTER_HTML is unset (build info is always shown)', async () => {
    // The footer always renders so build metadata is always visible.
    vi.stubEnv('VITE_FOOTER_HTML', '');
    let container!: HTMLElement;
    await act(async () => { ({ container } = render(<AppFooter />)); });
    expect(container.querySelector('footer')).not.toBeNull();
  });

  it('renders a link as a clickable anchor with the correct href', async () => {
    vi.stubEnv('VITE_FOOTER_HTML', '<a href="https://example.com">Company Portal</a>');
    await act(async () => { render(<AppFooter />); });
    const link = screen.getByRole('link', { name: 'Company Portal' });
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', 'https://example.com');
  });

  it('strips script tags from the rendered footer HTML', async () => {
    vi.stubEnv('VITE_FOOTER_HTML', '<div>Safe<script>alert("xss")</script></div>');
    await act(async () => { render(<AppFooter />); });
    const footer = screen.getByRole('contentinfo');
    expect(footer.querySelector('script')).toBeNull();
  });

  it('does not render event-handler attributes in the footer', async () => {
    vi.stubEnv('VITE_FOOTER_HTML', '<div onclick="evil()">Text</div>');
    await act(async () => { render(<AppFooter />); });
    const footer = screen.getByRole('contentinfo');
    const div = footer.querySelector('div');
    expect(div).not.toHaveAttribute('onclick');
  });

  it('shows "Frontend built:" label in the footer', async () => {
    await act(async () => { render(<AppFooter />); });
    const footer = screen.getByRole('contentinfo');
    expect(footer.textContent).toContain('Frontend built:');
  });

  it('shows "Backend built:" label in the footer', async () => {
    await act(async () => { render(<AppFooter />); });
    const footer = screen.getByRole('contentinfo');
    expect(footer.textContent).toContain('Backend built:');
  });
});


