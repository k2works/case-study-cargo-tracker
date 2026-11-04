import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router';
import { createApi } from '@/lib/graphql-client';
import { ApiProvider } from '@/providers/ApiProvider';
import { authHeaders, AuthProvider, type Role } from '@/providers/AuthProvider';
import { ReflectionConfigProvider, type ReflectionConfig } from '@/providers/ReflectionConfig';

export const API_BASE = 'http://api.test';
export const TEST_USER_ID = '01ARZ3NDEKTSV4RRFFQ69G5FAV';
/** テストでは短くする（本番の既定は 500ms 起点・上限 2s・15s で打ち切り）。 */
export const FAST_REFLECTION: ReflectionConfig = { initialMs: 10, maxMs: 20, timeoutMs: 400 };

export function renderApp(ui: ReactElement, options: { role?: Role | null; route?: string; reflection?: ReflectionConfig } = {}) {
  const { role = 'Sales', route = '/', reflection = FAST_REFLECTION } = options;
  const user = role ? { userId: TEST_USER_ID, role } : null;
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } } });
  const api = createApi({ baseUrl: API_BASE, headers: () => authHeaders(user) });
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider user={user}>
        <ApiProvider api={api}>
          <ReflectionConfigProvider value={reflection}>
            <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
          </ReflectionConfigProvider>
        </ApiProvider>
      </AuthProvider>
    </QueryClientProvider>,
  );
}
