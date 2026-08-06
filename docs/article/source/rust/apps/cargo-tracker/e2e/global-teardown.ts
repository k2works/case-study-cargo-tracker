import { execSync } from 'node:child_process';

/**
 * E2E 終了時に一時 Postgres コンテナを破棄する。
 */
export default function globalTeardown() {
  try {
    execSync('docker rm -f cargo-tracker-e2e-db', { stdio: 'ignore' });
  } catch {
    // 既に無い場合は無視する。
  }
}
