import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from 'react';

interface Toast {
  id: number;
  message: ReactNode;
  action?: { label: string; run: () => void };
}

const ToastContext = createContext<(t: Omit<Toast, 'id'>, ms?: number) => void>(() => {});

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const next = useRef(1);
  const push = useCallback((t: Omit<Toast, 'id'>, ms = 4200) => {
    const id = next.current++;
    setToasts((ts) => [...ts.slice(-2), { ...t, id }]);
    window.setTimeout(() => setToasts((ts) => ts.filter((x) => x.id !== id)), ms);
  }, []);
  const value = useMemo(() => push, [push]);
  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="toasts" role="status" aria-live="polite">
        {toasts.map((t) => (
          <div className="toast" key={t.id}>
            <span className="grow">{t.message}</span>
            {t.action && (
              <button
                type="button"
                className="btn btn--sm"
                onClick={() => {
                  t.action!.run();
                  setToasts((ts) => ts.filter((x) => x.id !== t.id));
                }}
              >
                {t.action.label}
              </button>
            )}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export const useToast = () => useContext(ToastContext);
