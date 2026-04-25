import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { type PropsWithChildren, useMemo } from 'react';
import { useApplyTheme } from '../hooks/useApplyTheme';

function ThemeBridge({ children }: PropsWithChildren) {
  useApplyTheme();
  return children;
}

export function AppProviders({ children }: PropsWithChildren) {
  const queryClient = useMemo(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 5_000,
            refetchOnWindowFocus: false
          }
        }
      }),
    []
  );

  return (
    <QueryClientProvider client={queryClient}>
      <ThemeBridge>{children}</ThemeBridge>
    </QueryClientProvider>
  );
}

