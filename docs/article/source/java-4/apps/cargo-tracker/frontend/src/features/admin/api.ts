import { commandClient, queryClient } from '@/shared/api/client';
import type { Pending } from '@/shared/api/pending';

/** S90 の 1 行。パスワードハッシュは載せない。 */
export interface AdminUserView {
  readonly username: string;
  readonly displayName: string;
  readonly roles: readonly string[];
  readonly enabled: boolean;
  readonly failedAttempts: number;
  readonly lockedUntil: string | null;
  readonly locked: boolean;
}

export function fetchAdminUsers(): Promise<Pending<{ users: AdminUserView[] }>> {
  return queryClient('/auth/admin/users');
}

/**
 * ロックを解く。
 *
 * <p>居ない利用者でも 204 が返る。404 と出し分けると、この画面を踏み台にして
 * 利用者名を総当たりできる。</p>
 */
export function unlockUser(username: string): Promise<void> {
  return commandClient(`/auth/admin/users/${encodeURIComponent(username)}/unlock`, {});
}
