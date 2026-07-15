import { useEffect, useRef } from 'react';

/**
 * A safe setInterval hook that clears on unmount.
 * Used for auto-refreshing chart data without leaks.
 */
export function useInterval(callback: () => void, delayMs: number | null): void {
  const savedCallback = useRef<() => void>(callback);

  useEffect(() => {
    savedCallback.current = callback;
  }, [callback]);

  useEffect(() => {
    if (delayMs === null) return;
    const id = setInterval(() => savedCallback.current(), delayMs);
    return () => clearInterval(id);
  }, [delayMs]);
}
