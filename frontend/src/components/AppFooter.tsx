import { useEffect, useState } from 'react';

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

interface BackendBuildInfo {
  buildDate: string;
  buildTime: string;
  buildTimestamp?: string;
  version: string;
}

function toUtcDisplay(isoTimestamp: string): string {
  const parsed = new Date(isoTimestamp);
  if (Number.isNaN(parsed.getTime())) {
    return isoTimestamp;
  }
  return parsed.toISOString().replace('T', ' ').replace(/\.\d{3}Z$/, ' UTC');
}

function formatBuildTimestampForUser(isoTimestamp: string | undefined, fallbackUtc: string): string {
  if (!isoTimestamp?.trim()) {
    return fallbackUtc;
  }

  const parsed = new Date(isoTimestamp);
  if (Number.isNaN(parsed.getTime())) {
    return fallbackUtc;
  }

  const locale =
    typeof navigator !== 'undefined' && typeof navigator.language === 'string' && navigator.language.trim()
      ? navigator.language
      : '';

  let timeZone = '';
  try {
    timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone ?? '';
  } catch {
    return fallbackUtc;
  }

  if (!locale || !timeZone) {
    return fallbackUtc;
  }

  try {
    return new Intl.DateTimeFormat(locale, {
      dateStyle: 'short',
      timeStyle: 'medium',
      timeZone,
    }).format(parsed);
  } catch {
    return fallbackUtc;
  }
}

/**
 * Renders a global sticky footer showing the frontend and backend build timestamps.
 * Optionally also renders sanitized custom HTML from the VITE_FOOTER_HTML env variable.
 * The backend build info is fetched from the public /api/about endpoint.
 */
export default function AppFooter() {
  const rawHtml = import.meta.env.VITE_FOOTER_HTML as string | undefined;
  // ISO timestamp injected by Vite's define at bundle/dev-server start time
  const frontendBuildTime = import.meta.env.VITE_BUILD_TIME as string | undefined;
  const [backendBuild, setBackendBuild] = useState<BackendBuildInfo | null>(null);

  useEffect(() => {
    const backendUrl = (import.meta.env.VITE_BACKEND_ROOT_URL as string | undefined) ?? '';
    // Use a plain fetch (not the axios api instance) because this endpoint is public
    // and the footer renders on unauthenticated pages (e.g. login, signup).
    fetch(`${backendUrl}/api/about`)
      .then(r => r.json())
      .then((data: BackendBuildInfo) => setBackendBuild(data))
      .catch(() => { /* silently ignore — backend may be unreachable during development */ });
  }, []);

  const sanitized = rawHtml?.trim() ? sanitizeHtml(rawHtml) : '';

  const frontendLabel = frontendBuildTime
    ? formatBuildTimestampForUser(frontendBuildTime, toUtcDisplay(frontendBuildTime))
    : 'dev';

  const backendLabel = backendBuild
    ? (backendBuild.buildDate === 'development'
        ? 'dev'
        : formatBuildTimestampForUser(
            backendBuild.buildTimestamp,
            `${backendBuild.buildDate} ${backendBuild.buildTime}`
          ))
    : '…';

  return (
    <footer className="w-full shrink-0 border-t border-slate-200 dark:border-slate-700/50 bg-white dark:bg-slate-900 py-1.5 px-4 text-xs text-slate-500 dark:text-slate-400 z-10 flex flex-wrap items-center justify-between gap-x-4 gap-y-1">
      {/* Optional custom HTML from VITE_FOOTER_HTML */}
      {sanitized.trim() && (
        <span dangerouslySetInnerHTML={{ __html: sanitized }} />
      )}

      {/* Build metadata */}
      <span className="ml-auto flex items-center gap-3 whitespace-nowrap">
        <span>
          Frontend built: <strong className="text-slate-700 dark:text-slate-300">{frontendLabel}</strong>
        </span>
        <span className="text-slate-300 dark:text-slate-600">|</span>
        <span>
          Backend built:{' '}
          <strong className="text-slate-700 dark:text-slate-300">{backendLabel}</strong>
          {backendBuild?.version && backendBuild.version !== 'development' && backendBuild.version !== 'unknown' && (
            <span className="ml-1 text-slate-400 dark:text-slate-500">v{backendBuild.version}</span>
          )}
        </span>
      </span>
    </footer>
  );
}
