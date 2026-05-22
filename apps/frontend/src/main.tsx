import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import './index.css';
import App from './App';

/**
 * フロントエンドのエントリポイント。
 *
 * StrictMode で開発時の問題を早期検出し、BrowserRouter で SPA の
 * クライアントサイドルーティングを有効化する。
 */
const rootElement = document.getElementById('root');
if (!rootElement) {
  throw new Error('Root element #root が見つかりません。index.html を確認してください。');
}

createRoot(rootElement).render(
  <StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
);
