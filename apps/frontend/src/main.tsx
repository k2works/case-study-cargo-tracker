import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'
import './index.css'
import App from './App'
import { installApiAuth } from './features/auth/install-api-auth'

const queryClient = new QueryClient()

// API クライアントに認証状態を繋ぐ。呼び忘れると業務 API がすべて 401 になる
installApiAuth()

/**
 * バックエンド未実装の間、画面と E2E を先に成立させるための API モック。
 *
 * 本番ビルドに混ざると「動いているように見えるだけの画面」を出荷することになるため、
 * 環境変数で明示的に有効化したときだけ起動する。
 */
async function startApiMock() {
  if (import.meta.env.VITE_ENABLE_API_MOCK !== 'true') {
    return
  }
  const { worker } = await import('./mocks/browser')
  await worker.start({ onUnhandledRequest: 'bypass' })
}

await startApiMock()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
)
