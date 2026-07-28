import type { AuthenticatedUser } from './authenticated-user.js';
import { isLockedByAttempts, nextFailedAttempts } from './account-lock-policy.js';
import type {
  PasswordVerifier,
  UserAccount,
  UserRepository,
} from './user-account.js';

export type AuthenticationResult =
  | { outcome: 'SUCCESS'; user: AuthenticatedUser }
  | { outcome: 'INVALID_CREDENTIALS' }
  | { outcome: 'LOCKED' }
  | { outcome: 'DISABLED' };

/**
 * ユーザー認証ユースケース。
 * 資格情報照合・アカウント無効判定・ログイン失敗ロック（US26 受入基準）を担う。
 */
export class AuthenticationService {
  constructor(
    private readonly users: UserRepository,
    private readonly passwords: PasswordVerifier,
  ) {}

  async authenticate(username: string, rawPassword: string): Promise<AuthenticationResult> {
    const account = await this.users.findByUsername(username);
    if (account === null) {
      return { outcome: 'INVALID_CREDENTIALS' };
    }
    if (!account.enabled) {
      return { outcome: 'DISABLED' };
    }
    if (isLockedByAttempts(account.failedAttempts)) {
      return { outcome: 'LOCKED' };
    }

    const matched = await this.passwords.verify(rawPassword, account.passwordHash);
    if (!matched) {
      return this.handleFailure(account);
    }

    await this.users.resetFailedAttempts(account.id);
    return { outcome: 'SUCCESS', user: toAuthenticatedUser(account) };
  }

  private async handleFailure(account: UserAccount): Promise<AuthenticationResult> {
    await this.users.incrementFailedAttempts(account.id);
    if (isLockedByAttempts(nextFailedAttempts(account.failedAttempts))) {
      return { outcome: 'LOCKED' };
    }
    return { outcome: 'INVALID_CREDENTIALS' };
  }
}

function toAuthenticatedUser(account: UserAccount): AuthenticatedUser {
  return {
    id: account.id,
    username: account.username,
    roles: account.roles,
  };
}
