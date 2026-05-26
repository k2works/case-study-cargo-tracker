/**
 * 認証トークンの永続化キーと、認証済みリクエスト用ヘッダの生成。
 *
 * トークンは {@link AuthContext} がログイン時に localStorage へ保存する。
 * 全 API クライアントはこのヘルパー経由で Authorization ヘッダを付与し、
 * 保存先（localStorage）とキー（auth_token）を一元管理する。
 */

/** 認証トークンの localStorage キー（AuthContext と共有する単一の真実）。 */
export const TOKEN_STORAGE_KEY = 'auth_token';

/** 認証済みリクエスト用の Authorization ヘッダを返す（未ログイン時は空）。 */
export function authHeader(): Record<string, string> {
  const token = localStorage.getItem(TOKEN_STORAGE_KEY);
  return token ? { Authorization: `Bearer ${token}` } : {};
}
