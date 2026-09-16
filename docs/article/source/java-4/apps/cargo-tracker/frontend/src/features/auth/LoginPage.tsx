import { useState, type SubmitEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { ApiError } from '@/shared/api/client';
import { useAuthStore } from '@/shared/auth/authStore';
import {
  BUTTON_PRIMARY,
  FIELD,
  LABEL,
  LINK,
  NOTICE,
  PAGE_TITLE,
} from '@/shared/ui/styles';
import { login } from './api';
import { DEMO_LOGIN } from './demoLogin';

/** S00 ログイン（UC20 / US26）。 */
export function LoginPage() {
  // 開発環境では動作確認用の利用者を事前入力する（既定は無効。ADR-0004）。
  const [username, setUsername] = useState(DEMO_LOGIN.username);
  const [password, setPassword] = useState(DEMO_LOGIN.password);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const signIn = useAuthStore((state) => state.login);
  const navigate = useNavigate();

  async function onSubmit(event: SubmitEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const result = await login(username, password);
      signIn({
        username: result.username,
        roles: result.roles,
        token: result.token,
      });
      navigate('/', { replace: true });
    } catch (e) {
      // 理由は API が返す 1 種類だけ。ここで足すと利用者名の存在を教えてしまう。
      setError(
        e instanceof ApiError
          ? e.body.message
          : '通信に失敗しました。しばらくしてからもう一度お試しください',
      );
    } finally {
      setSubmitting(false);
    }
  }

  // 背景は画面全体に敷く。main だけに敷くと、左右に白い余白が残って
  // 「途中で読み込みが止まった画面」に見える。
  return (
    <div className="min-h-screen bg-gray-50">
      <main className="mx-auto max-w-md p-8">
        <h1 className={PAGE_TITLE}>ログイン</h1>
        <p className="mt-1 text-gray-600">国際貨物輸送管理システム</p>

        {DEMO_LOGIN.enabled && (
          <section className="mt-6 space-y-3" aria-labelledby="demo-accounts">
            {/* 事前入力していることを隠さない。気づかないまま本番同様の画面だと
              思われるのが最も危ない。 */}
            <p className={NOTICE}>
              <strong>開発環境です。</strong> 動作確認用の利用者で事前入力しています。
            </p>

            <div className="rounded border border-gray-200 bg-white p-4">
              <h2
                id="demo-accounts"
                className="text-sm font-semibold text-gray-900"
              >
                動作確認用の利用者
              </h2>
              <p className="mt-1 text-sm text-gray-600">
                <strong>パスワードは共通</strong>で{' '}
                <code>{DEMO_LOGIN.password}</code> です。
                利用者名を選ぶと入力欄に反映されます。
              </p>

              <ul className="mt-3 divide-y divide-gray-100 text-sm">
                {DEMO_LOGIN.accounts.map((account) => (
                  <li key={account.username} className="flex gap-3 py-2">
                    <button
                      type="button"
                      onClick={() => {
                        setUsername(account.username);
                        setPassword(DEMO_LOGIN.password);
                        setError(null);
                      }}
                      className={`${LINK} w-32 shrink-0 text-left`}
                    >
                      {account.username}
                    </button>
                    <span
                      className={
                        account.canSignIn ? 'text-gray-700' : 'text-gray-500'
                      }
                    >
                      {account.description}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          </section>
        )}

        <form
          onSubmit={onSubmit}
          className="mt-8 space-y-4 rounded border border-gray-200 bg-white p-6"
        >
          <div>
            <label
              htmlFor="username"
              className={LABEL}
            >
              利用者名
            </label>
            <input
              id="username"
              name="username"
              autoComplete="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
              className={FIELD}
            />
          </div>

          <div>
            <label
              htmlFor="password"
              className={LABEL}
            >
              パスワード
            </label>
            <input
              id="password"
              name="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              className={FIELD}
            />
          </div>

          {error !== null && (
            <>
              <p role="alert" className="text-sm text-red-700">
                {error}
              </p>
              {/* US31 §受入基準 3・6。ロックされたことも、アカウントが無効で
                  あることも、その人にだけ伝えることはできない。伝えると、
                  利用者名が実在するかどうかを教えてしまう（同一メッセージに
                  している理由がそれ）。そこで「起こりうること」と「次に何を
                  すればよいか」を、失敗した全員に同じ文で出す。 */}
              <p className="text-sm text-gray-600">
                続けて 5 回失敗すると、アカウントは 15 分間ロックされます。
                心当たりがないのに入れないときは、システム管理者にお問い合わせください。
              </p>
            </>
          )}

          {/* 送信中は disabled でなく aria-disabled にしてフォーカスを保つ。
            disabled にすると読み上げの位置が飛び、キーボード利用者が迷う。 */}
          <button
            type="submit"
            aria-disabled={submitting}
            className={`${BUTTON_PRIMARY} w-full`}
          >
            {submitting ? 'ログイン中…' : 'ログイン'}
          </button>
        </form>

        <p className="mt-6 text-sm text-gray-600">
          荷物の追跡は
          <Link to="/portal" className={LINK}>
            ログインなしで照会できます
          </Link>
          。
        </p>
      </main>
    </div>
  );
}
