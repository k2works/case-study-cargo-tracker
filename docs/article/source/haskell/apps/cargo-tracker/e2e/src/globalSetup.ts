import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { resolve } from 'node:path';

/**
 * Playwright globalSetup (IT5 task 3.2 配線).
 *
 * 1. e2e-schema-setup.sh を実行し、cargo_tracker_e2e schema を用意 + migration + TRUNCATE
 * 2. dev サーバが `public` schema を向いている場合の fallback として、公開 schema の
 *    テスト対象テーブルも TRUNCATE する (test isolation)
 *
 * 環境変数:
 *   DATABASE_URL   : 必須 (Postgres 接続文字列)
 *   E2E_SKIP_SETUP : "1" 指定時は setup をスキップ (CI 側で別途セットアップ済のケース)
 *   E2E_TRUNCATE_PUBLIC : "0" 指定時は public schema の TRUNCATE をスキップ
 */
export default async function globalSetup(): Promise<void> {
  if (process.env.E2E_SKIP_SETUP === '1') {
    console.log('[globalSetup] E2E_SKIP_SETUP=1 のためスキップ');
    return;
  }

  const databaseUrl =
    process.env.DATABASE_URL ||
    'postgres://cargo_tracker:cargo_tracker_dev@localhost:5432/cargo_tracker_dev?sslmode=disable';

  const repoRoot = resolve(__dirname, '../../../..');
  const script = resolve(repoRoot, 'apps/cargo-tracker/scripts/e2e-schema-setup.sh');

  if (!existsSync(script)) {
    throw new Error(`[globalSetup] script not found: ${script}`);
  }

  console.log('[globalSetup] e2e-schema-setup.sh を実行します');
  execFileSync('bash', [script], {
    cwd: repoRoot,
    env: { ...process.env, DATABASE_URL: databaseUrl },
    stdio: 'inherit',
  });

  if (process.env.E2E_TRUNCATE_PUBLIC !== '0') {
    console.log('[globalSetup] public schema の TRUNCATE (dev サーバ向け fallback)');
    execFileSync(
      'psql',
      [
        databaseUrl,
        '-v',
        'ON_ERROR_STOP=1',
        '-c',
        `TRUNCATE TABLE
           confirmation_code, handling_activity, tracking_activity, session,
           customs_declaration, leg, itinerary, cargo, shipper,
           route_candidate, estimate, carrier_movement, voyage
         RESTART IDENTITY CASCADE;`,
      ],
      { stdio: 'inherit' },
    );
  }
}
