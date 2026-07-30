import type { AuthenticatedUser } from '../../infrastructure/auth/authenticated-user.js';

/**
 * express-session のセッションに認証済みユーザーとフラッシュメッセージを保持する。
 * ドメインオブジェクトはセッションに乗せない（frontend アーキテクチャ方針）。
 */
declare module 'express-session' {
  interface SessionData {
    user?: AuthenticatedUser;
    flash?: { error?: string; success?: string; warning?: string };
  }
}
