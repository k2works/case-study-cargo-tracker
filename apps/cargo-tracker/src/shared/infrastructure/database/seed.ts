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

/** 主要 UN/LOCODE マスタ（見積・予約の出発地/目的地で使用） */
const SEED_LOCATIONS: { unlocode: string; name: string; countryCode: string }[] = [
  { unlocode: 'JPTYO', name: 'Tokyo', countryCode: 'JP' },
  { unlocode: 'JPOSA', name: 'Osaka', countryCode: 'JP' },
  { unlocode: 'JPYOK', name: 'Yokohama', countryCode: 'JP' },
  { unlocode: 'USLAX', name: 'Los Angeles', countryCode: 'US' },
  { unlocode: 'USNYC', name: 'New York', countryCode: 'US' },
  { unlocode: 'SGSIN', name: 'Singapore', countryCode: 'SG' },
  { unlocode: 'CNSHA', name: 'Shanghai', countryCode: 'CN' },
  { unlocode: 'NLRTM', name: 'Rotterdam', countryCode: 'NL' },
  { unlocode: 'DEHAM', name: 'Hamburg', countryCode: 'DE' },
  { unlocode: 'HKHKG', name: 'Hong Kong', countryCode: 'HK' },
];

/**
 * location マスタを投入する（冪等）。
 */
export async function seedLocations(db: AppDatabase): Promise<void> {
  const existing = await db.selectFrom('location').select('id').executeTakeFirst();
  if (existing !== undefined) {
    return;
  }
  await db.insertInto('location').values(SEED_LOCATIONS).execute();
}
