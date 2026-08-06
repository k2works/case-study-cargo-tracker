import type { Role } from '../../domain/model/role.js';

/**
 * 認証対象のユーザーアカウント（Security 認証基盤の読取モデル）。
 * ドメインの境界付けられたコンテキストではなく横断的な認証基盤に属する。
 */
export interface UserAccount {
  readonly id: number;
  readonly username: string;
  readonly passwordHash: string;
  readonly roles: readonly Role[];
  readonly enabled: boolean;
  readonly failedAttempts: number;
}

/** 認証ユーザーの永続化ポート（インフラ層のアダプターが実装する） */
export interface UserRepository {
  findByUsername(username: string): Promise<UserAccount | null>;
  incrementFailedAttempts(userId: number): Promise<void>;
  resetFailedAttempts(userId: number): Promise<void>;
}

/** パスワード照合ポート（bcrypt アダプターが実装する） */
export interface PasswordVerifier {
  verify(rawPassword: string, passwordHash: string): Promise<boolean>;
}
