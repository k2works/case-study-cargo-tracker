import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import { AppRoutes } from './AppRoutes';
import { createApi } from '@/lib/graphql-client';
import { ApiProvider } from '@/providers/ApiProvider';
import { authHeaders, AuthProvider, ROLES, type CurrentUser } from '@/providers/AuthProvider';
import './styles.css';

// 開発用の認証スタブ（Bolt 2 で実際の認証に差し替える）。利用者とロールはビルド時の環境変数で渡す。
const role = ROLES.find((r) => r === import.meta.env.VITE_DEV_ROLE);
const userId = import.meta.env.VITE_DEV_USER_ID;
const user: CurrentUser | null = role && userId ? { userId, role } : null;

const queryClient = new QueryClient();
const api = createApi({ headers: () => authHeaders(user) });

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider user={user}>
        <ApiProvider api={api}>
          <BrowserRouter>
            <AppRoutes />
          </BrowserRouter>
        </ApiProvider>
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>,
);
