import React, { useCallback, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';

export type TooltipPosition = 'top' | 'bottom' | 'left' | 'right';

export interface TooltipProps {
  /** Text shown inside the floating popover. */
  content: string;
  /** Preferred side of the trigger to render the popover on. Defaults to 'top'. */
  position?: TooltipPosition;
  /** The trigger element (e.g. an icon button). Must accept refs/DOM event handlers. */
  children: React.ReactElement;
  /** Disables the tooltip entirely (e.g. while the trigger is disabled). */
  disabled?: boolean;
}

const GAP = 8;

/**
 * Lightweight, dependency-free popover/tooltip rendered in a portal so it always floats
 * above table rows/scroll containers (avoiding clipping from `overflow-x-auto` wrappers).
 * Shown on hover *and* keyboard focus so it stays accessible, and positioned from the
 * trigger's live bounding rect so it tracks the button wherever it sits on the page.
 * This is the single shared implementation reused by `TableActionButton` and any other
 * component that needs a hover/focus popover instead of duplicating tooltip logic.
 */
export const Tooltip: React.FC<TooltipProps> = ({ content, position = 'top', children, disabled = false }) => {
  const triggerRef = useRef<HTMLElement | null>(null);
  const [visible, setVisible] = useState(false);
  const [coords, setCoords] = useState<{ top: number; left: number } | null>(null);

  const [resolvedPosition, setResolvedPosition] = useState<TooltipPosition>(position);

  const updatePosition = useCallback(() => {
    const el = triggerRef.current;
    if (!el) return;
    const rect = el.getBoundingClientRect();
    // Flip vertically/horizontally when there isn't enough room on the preferred side,
    // so tooltips on the first row/first column of a table don't get cut off-screen.
    const TOOLTIP_SIZE_ESTIMATE = 40;
    let effective = position;
    if (position === 'top' && rect.top < TOOLTIP_SIZE_ESTIMATE) effective = 'bottom';
    else if (position === 'bottom' && window.innerHeight - rect.bottom < TOOLTIP_SIZE_ESTIMATE) effective = 'top';
    else if (position === 'left' && rect.left < TOOLTIP_SIZE_ESTIMATE) effective = 'right';
    else if (position === 'right' && window.innerWidth - rect.right < TOOLTIP_SIZE_ESTIMATE) effective = 'left';

    let top = 0;
    let left = 0;
    switch (effective) {
      case 'bottom':
        top = rect.bottom + GAP;
        left = rect.left + rect.width / 2;
        break;
      case 'left':
        top = rect.top + rect.height / 2;
        left = rect.left - GAP;
        break;
      case 'right':
        top = rect.top + rect.height / 2;
        left = rect.right + GAP;
        break;
      case 'top':
      default:
        top = rect.top - GAP;
        left = rect.left + rect.width / 2;
        break;
    }
    setResolvedPosition(effective);
    setCoords({ top, left });
  }, [position]);

  const show = useCallback(() => {
    if (disabled || !content) return;
    updatePosition();
    setVisible(true);
  }, [disabled, content, updatePosition]);

  const hide = useCallback(() => setVisible(false), []);

  useLayoutEffect(() => {
    if (!visible) return;
    updatePosition();
    const onScrollOrResize = () => updatePosition();
    window.addEventListener('scroll', onScrollOrResize, true);
    window.addEventListener('resize', onScrollOrResize);
    return () => {
      window.removeEventListener('scroll', onScrollOrResize, true);
      window.removeEventListener('resize', onScrollOrResize);
    };
  }, [visible, updatePosition]);

  const translate =
    resolvedPosition === 'top' ? 'translate(-50%, -100%)'
      : resolvedPosition === 'bottom' ? 'translate(-50%, 0)'
      : resolvedPosition === 'left' ? 'translate(-100%, -50%)'
      : 'translate(0, -50%)';

  const arrowClasses =
    resolvedPosition === 'top' ? 'top-full left-1/2 -translate-x-1/2 -mt-px border-t-slate-900 dark:border-t-slate-700 border-x-transparent border-b-transparent'
      : resolvedPosition === 'bottom' ? 'bottom-full left-1/2 -translate-x-1/2 -mb-px border-b-slate-900 dark:border-b-slate-700 border-x-transparent border-t-transparent'
      : resolvedPosition === 'left' ? 'left-full top-1/2 -translate-y-1/2 -ml-px border-l-slate-900 dark:border-l-slate-700 border-y-transparent border-r-transparent'
      : 'right-full top-1/2 -translate-y-1/2 -mr-px border-r-slate-900 dark:border-r-slate-700 border-y-transparent border-l-transparent';

  const setRefs = useCallback((node: HTMLElement | null) => {
    triggerRef.current = node;
  }, []);

  const childProps = children.props as Record<string, unknown>;

  const trigger = React.cloneElement(children, {
    ref: setRefs,
    onMouseEnter: (e: React.MouseEvent) => {
      (childProps.onMouseEnter as ((e: React.MouseEvent) => void) | undefined)?.(e);
      show();
    },
    onMouseLeave: (e: React.MouseEvent) => {
      (childProps.onMouseLeave as ((e: React.MouseEvent) => void) | undefined)?.(e);
      hide();
    },
    onFocus: (e: React.FocusEvent) => {
      (childProps.onFocus as ((e: React.FocusEvent) => void) | undefined)?.(e);
      show();
    },
    onBlur: (e: React.FocusEvent) => {
      (childProps.onBlur as ((e: React.FocusEvent) => void) | undefined)?.(e);
      hide();
    },
  } as unknown as React.Attributes);

  return (
    <>
      {trigger}
      {visible && coords && content
        ? createPortal(
            <div
              role="tooltip"
              style={{ position: 'fixed', top: coords.top, left: coords.left, transform: translate, maxWidth: 260 }}
              className="z-[1000] pointer-events-none animate-fade-in w-max"
            >
              <div className="relative px-2.5 py-1.5 rounded-lg bg-slate-900 dark:bg-slate-700 text-white text-xs font-medium shadow-lg text-center break-words">
                {content}
                <div className={`absolute h-0 w-0 border-4 ${arrowClasses}`} />
              </div>
            </div>,
            document.body
          )
        : null}
    </>
  );
};

export default Tooltip;
