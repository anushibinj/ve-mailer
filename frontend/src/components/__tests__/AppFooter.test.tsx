import { describe, it, expect, afterEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
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
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('renders nothing when VITE_FOOTER_HTML is empty', () => {
    vi.stubEnv('VITE_FOOTER_HTML', '');
    const { container } = render(<AppFooter />);
    expect(container.firstChild).toBeNull();
  });

  it('renders nothing when VITE_FOOTER_HTML is whitespace only', () => {
    vi.stubEnv('VITE_FOOTER_HTML', '   ');
    const { container } = render(<AppFooter />);
    expect(container.firstChild).toBeNull();
  });

  it('renders a footer element when HTML content is provided', () => {
    vi.stubEnv('VITE_FOOTER_HTML', '<div>Powered by VE Mailer</div>');
    render(<AppFooter />);
    expect(screen.getByRole('contentinfo')).toBeInTheDocument();
  });

  it('carries stacking and layout classes needed for the app-shell sticky footer', () => {
    // z-10 keeps the footer above scrollable content; shrink-0 prevents the
    // flex container from compressing the footer; w-full ensures it spans the viewport.
    vi.stubEnv('VITE_FOOTER_HTML', '<div>Footer</div>');
    render(<AppFooter />);
    const footer = screen.getByRole('contentinfo');
    expect(footer.className).toContain('z-10');
    expect(footer.className).toContain('shrink-0');
    expect(footer.className).toContain('w-full');
  });

  it('no footer element is present in the DOM when VITE_FOOTER_HTML is unset (no bottom spacing)', () => {
    // When no footer is configured, the layout should have no footer-related DOM node,
    // ensuring the flex-1 content area fills the full app-shell height without gap.
    vi.stubEnv('VITE_FOOTER_HTML', '');
    const { container } = render(<AppFooter />);
    expect(container.querySelector('footer')).toBeNull();
  });

  it('renders a link as a clickable anchor with the correct href', () => {
    vi.stubEnv('VITE_FOOTER_HTML', '<a href="https://example.com">Company Portal</a>');
    render(<AppFooter />);
    const link = screen.getByRole('link', { name: 'Company Portal' });
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', 'https://example.com');
  });

  it('strips script tags from the rendered footer HTML', () => {
    vi.stubEnv('VITE_FOOTER_HTML', '<div>Safe<script>alert("xss")</script></div>');
    render(<AppFooter />);
    const footer = screen.getByRole('contentinfo');
    expect(footer.querySelector('script')).toBeNull();
  });

  it('does not render event-handler attributes in the footer', () => {
    vi.stubEnv('VITE_FOOTER_HTML', '<div onclick="evil()">Text</div>');
    render(<AppFooter />);
    const footer = screen.getByRole('contentinfo');
    const div = footer.querySelector('div');
    expect(div).not.toHaveAttribute('onclick');
  });
});
