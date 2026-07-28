import type { ReactElement } from 'react';
import { Layout } from '../layout/Layout.js';
import { ALL_ROLES, ROLE_LABELS } from '../../shared/domain/model/role.js';

interface LoginProps {
  csrfToken?: string;
  error?: string;
  timeout?: boolean;
  /** 開発・デモ用の初期入力値（シードユーザー） */
  defaultUsername?: string;
  defaultPassword?: string;
  /** 開発・デモ環境でシードアカウント一覧を表示する */
  showDevAccounts?: boolean;
}

/** シードユーザーの username はロール名から ROLE_ を除いた小文字（seed.ts と一致） */
const DEV_ACCOUNTS = ALL_ROLES.map((role) => ({
  username: role.replace('ROLE_', '').toLowerCase(),
  label: ROLE_LABELS[role],
}));
const DEV_PASSWORD = 'password';

/**
 * ログイン画面（/login）。ui_design.md「ログイン画面」に準拠する。
 */
export function Login({
  csrfToken,
  error,
  timeout,
  defaultUsername,
  defaultPassword,
  showDevAccounts,
}: LoginProps): ReactElement {
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
                defaultValue={defaultUsername ?? ''}
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
                defaultValue={defaultPassword ?? ''}
                required
              />
            </div>
            <button type="submit" className="btn btn-primary w-100" data-testid="login-submit">
              ログイン
            </button>
          </form>

          {showDevAccounts && (
            <div className="card mt-4" data-testid="dev-accounts">
              <div className="card-body">
                <h2 className="h6 card-title">開発用アカウント（ロール別）</h2>
                <p className="small text-muted mb-2">
                  パスワードはすべて <code>{DEV_PASSWORD}</code> です。利用者 ID を差し替えて各ロールの画面を確認できます。
                </p>
                <table className="table table-sm mb-0">
                  <thead>
                    <tr>
                      <th scope="col">ロール</th>
                      <th scope="col">利用者 ID</th>
                    </tr>
                  </thead>
                  <tbody>
                    {DEV_ACCOUNTS.map((acc) => (
                      <tr key={acc.username}>
                        <td>{acc.label}</td>
                        <td>
                          <code>{acc.username}</code>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      </div>
    </Layout>
  );
}
