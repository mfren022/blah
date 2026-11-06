import { useEffect, useRef, useState } from 'react';

export interface AsyncState<T> {
  data?: T;
  error?: Error;
  loading: boolean;
}

/**
 * Run an async loader whenever `key` changes. Previous data is kept while the next
 * load runs (so lists don't flash), and late results from stale keys are ignored.
 */
export function useAsync<T>(key: string | null, load: () => Promise<T>): AsyncState<T> {
  const [state, setState] = useState<AsyncState<T>>({ loading: key !== null });
  const loadRef = useRef(load);
  loadRef.current = load;
  useEffect(() => {
    if (key === null) {
      setState({ loading: false });
      return;
    }
    let cancelled = false;
    setState((s) => ({ data: s.data, loading: true }));
    loadRef
      .current()
      .then((data) => !cancelled && setState({ data, loading: false }))
      .catch((error: Error) => !cancelled && setState({ error, loading: false }));
    return () => {
      cancelled = true;
    };
  }, [key]);
  return state;
}
