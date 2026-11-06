import { useEffect, useId, useRef, useState, type ReactNode } from 'react';

/** A small popover menu anchored to a button. Closes on outside click and Escape. */
export function Menu({
  trigger,
  children,
  align = 'left',
  label,
}: {
  trigger: (props: { open: boolean; toggle: () => void; id: string }) => ReactNode;
  children: (close: () => void) => ReactNode;
  align?: 'left' | 'right';
  label: string;
}) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const id = useId();
  useEffect(() => {
    if (!open) return;
    const onDown = (e: PointerEvent) => {
      if (!ref.current?.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        const items = [...(ref.current?.querySelectorAll<HTMLElement>('[role="menuitem"],[role="menuitemradio"]') ?? [])];
        const i = items.indexOf(document.activeElement as HTMLElement);
        const next = items[(i + (e.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length];
        next?.focus();
        e.preventDefault();
      }
    };
    document.addEventListener('pointerdown', onDown);
    document.addEventListener('keydown', onKey);
    requestAnimationFrame(() => ref.current?.querySelector<HTMLElement>('[role="menuitem"],[role="menuitemradio"]')?.focus());
    return () => {
      document.removeEventListener('pointerdown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);
  return (
    <div className="menu" ref={ref}>
      {trigger({ open, toggle: () => setOpen((o) => !o), id })}
      {open && (
        <div className={`menu__panel menu__panel--${align}`} role="menu" id={id} aria-label={label}>
          {children(() => setOpen(false))}
        </div>
      )}
    </div>
  );
}
