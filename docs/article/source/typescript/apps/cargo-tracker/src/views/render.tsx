import type { Response } from 'express';
import type { ReactElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';

/**
 * TSX コンポーネントをフルページ（<!DOCTYPE html> 付き）としてレンダリングし、レスポンスへ書き出す。
 * クライアント React・ハイドレーションは行わない（renderToStaticMarkup）。
 */
export function renderPage(res: Response, element: ReactElement): void {
  const html = renderToStaticMarkup(element);
  res.type('text/html').send(`<!DOCTYPE html>${html}`);
}

/**
 * htmx の部分更新用に、フラグメント（DOCTYPE なし）をレンダリングして書き出す。
 */
export function renderFragment(res: Response, element: ReactElement): void {
  const html = renderToStaticMarkup(element);
  res.type('text/html').send(html);
}

/**
 * テスト・組み立て用にフルページ HTML 文字列を返す。
 */
export function renderPageToString(element: ReactElement): string {
  return `<!DOCTYPE html>${renderToStaticMarkup(element)}`;
}
