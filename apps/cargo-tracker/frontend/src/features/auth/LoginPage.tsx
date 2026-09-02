import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router';
import { ApiError } from '@/shared/api/client';
import { useAuthStore } from '@/shared/auth/authStore';
import { login } from './api';

/** S00 ログイン（UC20 / US26）。 */
export function LoginPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const signIn = useAuthStore((state) => state.login);
  const navigate = useNavigate();

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const result = await login(username, password);
      signIn({ username: result.username, roles: result.roles, token: result.token });
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

  return (
    <main>
      <h1>ログイン</h1>
      <form onSubmit={onSubmit}>
        <label htmlFor="username">利用者名</label>
        <input
          id="username"
          name="username"
          autoComplete="username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          required
        />

        <label htmlFor="password">パスワード</label>
        <input
          id="password"
          name="password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />

        {/* 送信中は disabled でなく aria-disabled にしてフォーカスを保つ。
            disabled にすると読み上げの位置が飛び、キーボード利用者が迷う。 */}
        <button type="submit" aria-disabled={submitting}>
          {submitting ? 'ログイン中…' : 'ログイン'}
        </button>
      </form>

      {error !== null && <p role="alert">{error}</p>}

      <p>
        荷物の追跡は<a href="/tracking/public">ログインなしで照会できます</a>。
      </p>
    </main>
  );
}
