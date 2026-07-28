import '../src/shared/presentation/auth/session.js';
import { Test } from '@nestjs/testing';
import type { INestApplication } from '@nestjs/common';
import type { NestExpressApplication } from '@nestjs/platform-express';
import { AppModule } from '../src/app.module.js';
import { DATABASE, type AppDatabase } from '../src/shared/infrastructure/database/database.js';
import { createPgMemDatabase } from '../src/shared/infrastructure/database/pgmem-database.js';
import { createSessionMiddleware } from '../src/shared/infrastructure/config/session.config.js';
import { BcryptPasswordVerifier } from '../src/shared/infrastructure/auth/bcrypt-password-verifier.js';
import type { Role } from '../src/shared/domain/model/role.js';

export interface TestApp {
  app: INestApplication;
  db: AppDatabase;
}

/**
 * pg-mem を注入した Nest アプリを組み立てる（統合テスト用）。
 * セッションミドルウェアも本番同様に適用する。
 */
export async function createTestApp(): Promise<TestApp> {
  const { db } = createPgMemDatabase();
  const moduleRef = await Test.createTestingModule({
    imports: [AppModule],
  })
    .overrideProvider(DATABASE)
    .useValue(db)
    .compile();

  const app = moduleRef.createNestApplication<NestExpressApplication>();
  app.use(createSessionMiddleware());
  await app.init();
  return { app, db };
}

/** テストユーザーを作成する（bcrypt ハッシュ済みパスワード） */
export async function seedUser(
  db: AppDatabase,
  params: {
    username: string;
    password: string;
    roles: Role[];
    enabled?: boolean;
    failedAttempts?: number;
  },
): Promise<number> {
  const hash = await BcryptPasswordVerifier.hash(params.password);
  const inserted = await db
    .insertInto('users')
    .values({
      username: params.username,
      email: `${params.username}@example.com`,
      password: hash,
      enabled: params.enabled ?? true,
      failedLoginAttempts: params.failedAttempts ?? 0,
    })
    .returning('id')
    .executeTakeFirstOrThrow();
  if (params.roles.length > 0) {
    await db
      .insertInto('user_roles')
      .values(params.roles.map((role) => ({ userId: inserted.id, role })))
      .execute();
  }
  return inserted.id;
}
