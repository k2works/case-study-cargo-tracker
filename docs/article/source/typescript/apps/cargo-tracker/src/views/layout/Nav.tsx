import type { ReactElement } from 'react';
import type { AuthenticatedUser } from '../../shared/infrastructure/auth/authenticated-user.js';
import { visibleNavItems } from './nav-items.js';

interface NavProps {
  user: AuthenticatedUser;
  activePath?: string;
}

/**
 * 共通ナビゲーションバー（Bootstrap 5 navbar）。
 * ロールに応じてメニュー項目を表示制御する。
 */
export function Nav({ user, activePath }: NavProps): ReactElement {
  const items = visibleNavItems(user.roles);
  return (
    <nav className="navbar navbar-expand-lg navbar-dark bg-dark">
      <div className="container-fluid">
        <a className="navbar-brand" href="/">
          CargoTracker
        </a>
        <div className="navbar-nav me-auto">
          {items.map((item) => (
            <a
              key={item.href}
              className={`nav-link${activePath === item.href ? ' active' : ''}`}
              href={item.href}
              data-testid={`nav-${item.href}`}
            >
              {item.label}
            </a>
          ))}
        </div>
        <div className="navbar-nav">
          <span className="navbar-text me-3" data-testid="nav-username">
            {user.username}
          </span>
          <form action="/logout" method="post" className="d-inline">
            <button type="submit" className="btn btn-outline-light btn-sm" data-testid="nav-logout">
              ログアウト
            </button>
          </form>
        </div>
      </div>
    </nav>
  );
}
