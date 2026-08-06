import session from 'express-session';
import type { RequestHandler } from 'express';

/**
 * express-session ミドルウェアを生成する。
 * セッションストアは初期リリースではメモリストア（将来 DB ストアへ移行）。
 */
export function createSessionMiddleware(): RequestHandler {
  const secret = resolveSessionSecret();
  return session({
    secret,
    resave: false,
    saveUninitialized: false,
    cookie: {
      httpOnly: true,
      maxAge: 1000 * 60 * 30, // 30 分
      secure: process.env.NODE_ENV === 'production',
    },
  });
}

/**
 * セッションシークレットを解決する。
 * 本番（NODE_ENV=production）では SESSION_SECRET を必須とし、未設定なら起動を中断する。
 * 開発時のみ固定のフォールバック値を許可する。
 * @returns セッション署名用シークレット
 */
export function resolveSessionSecret(): string {
  const secret = process.env.SESSION_SECRET;
  if (secret) {
    return secret;
  }
  if (process.env.NODE_ENV === 'production') {
    throw new Error('本番環境では SESSION_SECRET を設定してください');
  }
  return 'cargo-tracker-dev-secret';
}
