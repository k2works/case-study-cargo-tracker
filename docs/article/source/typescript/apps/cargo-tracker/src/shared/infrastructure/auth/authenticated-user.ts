import type { Role } from '../../domain/model/role.js';

/**
 * 認証済みユーザーのビューモデル。
 * セッションに保持し、TSX テンプレートへ型付き props として渡す。
 * ドメインオブジェクトはセッションに乗せない（frontend アーキテクチャ方針）。
 */
export interface AuthenticatedUser {
  readonly id: number;
  readonly username: string;
  readonly roles: readonly Role[];
}

/** 指定ロールのいずれかを持つか判定する */
export function hasAnyRole(user: AuthenticatedUser, roles: readonly Role[]): boolean {
  return user.roles.some((r) => roles.includes(r));
}
