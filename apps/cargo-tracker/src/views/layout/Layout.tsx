import type { ReactElement, ReactNode } from 'react';
import type { AuthenticatedUser } from '../../shared/infrastructure/auth/authenticated-user.js';
import { Nav } from './Nav.js';

interface LayoutProps {
  title: string;
  user?: AuthenticatedUser;
  activePath?: string;
  csrfToken?: string;
  children: ReactNode;
}

/**
 * 共通レイアウト（children 合成）。
 * Bootstrap 5.3 / htmx 2.x を <head> で読み込み、認証済みなら navbar を表示する。
 */
export function Layout({
  title,
  user,
  activePath,
  csrfToken,
  children,
}: LayoutProps): ReactElement {
  return (
    <html lang="ja">
      <head>
        <meta charSet="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        {csrfToken !== undefined && <meta name="_csrf" content={csrfToken} />}
        {csrfToken !== undefined && <meta name="_csrf_header" content="x-csrf-token" />}
        <title>{`${title} | CargoTracker`}</title>
        <link rel="stylesheet" href="/vendor/bootstrap/bootstrap.min.css" />
      </head>
      <body>
        {user && <Nav user={user} activePath={activePath} />}
        <main className="container py-4">{children}</main>
        <script src="/vendor/htmx/htmx.min.js" />
        <script src="/js/app.js" />
      </body>
    </html>
  );
}
