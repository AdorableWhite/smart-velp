import { useEffect } from 'react';
import { usePreferencesStore } from '../store/usePreferencesStore';

function getResolvedTheme(appearance: 'system' | 'light' | 'dark') {
  if (appearance !== 'system') {
    return appearance;
  }

  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

export function useApplyTheme() {
  const appearance = usePreferencesStore((state) => state.appearance);

  useEffect(() => {
    const root = document.documentElement;
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');

    const apply = () => {
      root.dataset.theme = getResolvedTheme(appearance);
    };

    apply();
    mediaQuery.addEventListener('change', apply);
    return () => mediaQuery.removeEventListener('change', apply);
  }, [appearance]);
}

