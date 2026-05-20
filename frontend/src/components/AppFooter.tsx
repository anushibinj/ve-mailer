// Tags that are completely removed along with their entire subtree.
const DANGEROUS_TAGS = new Set([
  'script', 'iframe', 'object', 'embed', 'form', 'input', 'button',
  'style', 'noscript', 'meta', 'link', 'base', 'template', 'svg', 'math',
]);

// Only these tags are permitted; all others have their wrapper stripped (content preserved).
const ALLOWED_TAGS = new Set([
  'div', 'span', 'a', 'p', 'small', 'strong', 'em', 'br', 'ul', 'ol', 'li',
]);

function escapeText(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

function sanitizeNode(node: Node): string {
  if (node.nodeType === Node.TEXT_NODE) {
    return escapeText(node.textContent ?? '');
  }
  if (node.nodeType !== Node.ELEMENT_NODE) {
    return '';
  }

  const el = node as Element;
  const tag = el.tagName.toLowerCase();

  // Completely drop dangerous elements and their subtrees.
  if (DANGEROUS_TAGS.has(tag)) {
    return '';
  }

  // Recursively sanitize children first.
  const childContent = Array.from(el.childNodes).map(sanitizeNode).join('');

  // For harmless-but-disallowed tags, strip the element wrapper and keep inner content.
  if (!ALLOWED_TAGS.has(tag)) {
    return childContent;
  }

  // Build a safe attribute list for allowed elements.
  const safeAttrs: string[] = [];
  for (const attr of Array.from(el.attributes)) {
    const name = attr.name.toLowerCase();
    const value = attr.value;

    // Strip all event-handler attributes (onclick, onerror, onload, …).
    if (name.startsWith('on')) continue;

    // Strip javascript: and data: URLs from link/source attributes.
    if (['href', 'src', 'action'].includes(name) && /^\s*(javascript|data):/i.test(value)) {
      continue;
    }

    const generalAllowed = new Set(['class', 'style', 'id', 'title', 'aria-label', 'aria-hidden', 'role']);
    const anchorAllowed = new Set(['href', 'target', 'rel']);

    if (generalAllowed.has(name) || (tag === 'a' && anchorAllowed.has(name))) {
      safeAttrs.push(`${name}="${value.replace(/"/g, '&quot;')}"`);
    }
  }

  const attrStr = safeAttrs.length > 0 ? ' ' + safeAttrs.join(' ') : '';

  // br is a void element — no closing tag.
  if (tag === 'br') {
    return `<br${attrStr}>`;
  }

  return `<${tag}${attrStr}>${childContent}</${tag}>`;
}

/**
 * Sanitizes raw HTML to a safe subset of tags and attributes.
 * Exported for unit testing.
 */
// eslint-disable-next-line react-refresh/only-export-components -- utility exported for tests only
export function sanitizeHtml(html: string): string {
  if (typeof window === 'undefined') return '';

  const parser = new DOMParser();
  const doc = parser.parseFromString(`<div>${html}</div>`, 'text/html');
  const root = doc.body.firstElementChild;
  if (!root) return '';

  return Array.from(root.childNodes).map(sanitizeNode).join('');
}

/**
 * Renders the VITE_FOOTER_HTML environment variable as sanitized HTML at the
 * bottom of the application. Returns null (renders nothing) when the variable
 * is empty or undefined.
 */
export default function AppFooter() {
  const rawHtml = import.meta.env.VITE_FOOTER_HTML as string | undefined;

  if (!rawHtml?.trim()) {
    return null;
  }

  const sanitized = sanitizeHtml(rawHtml);

  if (!sanitized.trim()) {
    return null;
  }

  return (
    <footer
      className="w-full shrink-0 border-t border-gray-200 bg-white py-2 text-xs text-gray-500 z-10"
      dangerouslySetInnerHTML={{ __html: sanitized }}
    />
  );
}
