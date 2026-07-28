import session from 'express-session';
import type { RequestHandler } from 'express';

/**
 * express-session ミドルウェアを生成する。
 * セッションストアは初期リリースではメモリストア（将来 DB ストアへ移行）。
 */
export function createSessionMiddleware(): RequestHandler {
  const secret = process.env.SESSION_SECRET ?? 'cargo-tracker-dev-secret';
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
