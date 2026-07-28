import { ALL_ROLES, type Role } from '../../domain/model/role.js';
import { BcryptPasswordVerifier } from '../auth/bcrypt-password-verifier.js';
import type { AppDatabase } from './database.js';

/** 各ロールのデフォルトテストユーザー（username = ロール小文字, password 共通） */
const DEFAULT_PASSWORD = 'password';

const SEED_USERS: { username: string; roles: Role[] }[] = ALL_ROLES.map((role) => ({
  username: role.replace('ROLE_', '').toLowerCase(),
  roles: [role],
}));

/**
 * 開発・デモ用のシードデータを投入する。
 * 既にユーザーが存在する場合は何もしない（冪等）。
 */
export async function seedDefaultUsers(db: AppDatabase): Promise<void> {
  const existing = await db.selectFrom('users').select('id').executeTakeFirst();
  if (existing !== undefined) {
    return;
  }
  const hash = await BcryptPasswordVerifier.hash(DEFAULT_PASSWORD);
  for (const seed of SEED_USERS) {
    const user = await db
      .insertInto('users')
      .values({
        username: seed.username,
        email: `${seed.username}@example.com`,
        password: hash,
      })
      .returning('id')
      .executeTakeFirstOrThrow();
    await db
      .insertInto('user_roles')
      .values(seed.roles.map((role) => ({ userId: user.id, role })))
      .execute();
  }
}
