import type { ReactElement } from 'react';
import { Layout } from '../layout/Layout.js';

interface LoginProps {
  csrfToken?: string;
  error?: string;
  timeout?: boolean;
}

/**
 * ログイン画面（/login）。ui_design.md「ログイン画面」に準拠する。
 */
export function Login({ csrfToken, error, timeout }: LoginProps): ReactElement {
  return (
    <Layout title="ログイン" csrfToken={csrfToken}>
      <div className="row justify-content-center">
        <div className="col-md-5">
          <h1 className="h3 mb-4 text-center">CargoTracker ログイン</h1>
          {timeout && (
            <div className="alert alert-warning" role="alert" data-testid="login-timeout">
              セッションがタイムアウトしました。再度ログインしてください。
            </div>
          )}
          {error && (
            <div className="alert alert-danger" role="alert" data-testid="login-error">
              {error}
            </div>
          )}
          <form action="/login" method="post">
            {csrfToken !== undefined && (
              <input type="hidden" name="_csrf" value={csrfToken} />
            )}
            <div className="mb-3">
              <label htmlFor="username" className="form-label">
                利用者 ID
              </label>
              <input
                type="text"
                className="form-control"
                id="username"
                name="username"
                required
                autoFocus
              />
            </div>
            <div className="mb-3">
              <label htmlFor="password" className="form-label">
                パスワード
              </label>
              <input
                type="password"
                className="form-control"
                id="password"
                name="password"
                required
              />
            </div>
            <button type="submit" className="btn btn-primary w-100" data-testid="login-submit">
              ログイン
            </button>
          </form>
        </div>
      </div>
    </Layout>
  );
}
