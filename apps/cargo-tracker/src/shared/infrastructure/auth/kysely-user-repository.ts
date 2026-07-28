import { sql } from 'kysely';
import type { AppDatabase } from '../database/database.js';
import { isRole, type Role } from '../../domain/model/role.js';
import type { UserAccount, UserRepository } from './user-account.js';

/**
 * UserRepository の Kysely 実装（認証基盤の永続化アダプター）。
 */
export class KyselyUserRepository implements UserRepository {
  constructor(private readonly db: AppDatabase) {}

  async findByUsername(username: string): Promise<UserAccount | null> {
    const row = await this.db
      .selectFrom('users')
      .selectAll()
      .where('username', '=', username)
      .executeTakeFirst();
    if (row === undefined) {
      return null;
    }

    const roleRows = await this.db
      .selectFrom('user_roles')
      .select('role')
      .where('userId', '=', row.id)
      .execute();
    const roles: Role[] = roleRows.map((r) => r.role).filter(isRole);

    return {
      id: row.id,
      username: row.username,
      passwordHash: row.password,
      roles,
      enabled: row.enabled,
      failedAttempts: row.failedLoginAttempts,
    };
  }

  async incrementFailedAttempts(userId: number): Promise<void> {
    await this.db
      .updateTable('users')
      .set({ failedLoginAttempts: sql`failed_login_attempts + 1` })
      .where('id', '=', userId)
      .execute();
  }

  async resetFailedAttempts(userId: number): Promise<void> {
    await this.db
      .updateTable('users')
      .set({ failedLoginAttempts: 0 })
      .where('id', '=', userId)
      .execute();
  }
}
