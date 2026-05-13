'use strict';

/**
 * Heroku デプロイタスク
 *
 * 提供タスク:
 *   - deploy:dev:setup      : 初回セットアップ（アプリ作成 + Config Vars + ビルド + デプロイ）
 *   - deploy:dev            : 全サービスを更新デプロイ
 *   - deploy:dev:push:authms     : authms をビルド・プッシュ
 *   - deploy:dev:release:authms  : authms をリリース
 *   - deploy:dev:push:bookingms  : bookingms をビルド・プッシュ
 *   - deploy:dev:release:bookingms : bookingms をリリース
 *   - deploy:dev:push:frontend   : frontend をビルド・プッシュ
 *   - deploy:dev:release:frontend : frontend をリリース
 *   - deploy:dev:logs:authms     : authms のログを表示
 *   - deploy:dev:logs:bookingms  : bookingms のログを表示
 *   - deploy:dev:logs:frontend   : frontend のログを表示
 *
 * 必要な環境変数（.env）:
 *   - DEV_HEROKU_APP_PREFIX   : Heroku アプリ名のプレフィックス（例: ct）
 *   - DEV_DOCKER_PLATFORM     : Docker プラットフォーム（デフォルト: linux/amd64）
 *
 * @param {import('gulp').Gulp} gulp
 */

import { spawn } from 'child_process';
import path from 'path';
import { fileURLToPath } from 'url';
import { cleanDockerEnv } from './shared.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..', '..');
const backendDir = path.join(repoRoot, 'apps', 'backend');
const frontendDir = path.join(repoRoot, 'apps', 'frontend');

const isWindows = process.platform === 'win32';
const gradlewCmd = isWindows ? 'gradlew.bat' : './gradlew';

function getPrefix() {
  return process.env.DEV_HEROKU_APP_PREFIX || 'ct';
}

function getPlatform() {
  return process.env.DEV_DOCKER_PLATFORM || 'linux/amd64';
}

function appName(service) {
  return `${getPrefix()}-${service}`;
}

function runStreaming(cmd, args, opts = {}) {
  return new Promise((resolve) => {
    console.log(`> ${cmd} ${args.join(' ')}${opts.cwd ? ` (cwd=${opts.cwd})` : ''}`);
    const env = { ...cleanDockerEnv(), DOCKER_DEFAULT_PLATFORM: getPlatform() };
    const child = spawn(cmd, args, {
      stdio: 'inherit',
      shell: isWindows,
      env,
      ...opts,
    });
    child.on('close', (code) => {
      resolve(code === 0 ? null : new Error(`${cmd} exited with code ${code}`));
    });
  });
}

export default function (gulp) {
  // ── バックエンド ビルド ──────────────────────────────────────
  gulp.task('deploy:dev:build:backend', async (done) => {
    const err = await runStreaming(
      gradlewCmd,
      [':authms:bootJar', ':bookingms:bootJar'],
      { cwd: backendDir }
    );
    done(err);
  });

  // ── authms ──────────────────────────────────────────────────
  gulp.task('deploy:dev:push:authms', async (done) => {
    const err = await runStreaming(
      'heroku',
      ['container:push', 'web', '-a', appName('authms')],
      { cwd: path.join(backendDir, 'authms') }
    );
    done(err);
  });

  gulp.task('deploy:dev:release:authms', async (done) => {
    const err = await runStreaming(
      'heroku',
      ['container:release', 'web', '-a', appName('authms')]
    );
    done(err);
  });

  gulp.task('deploy:dev:logs:authms', async (done) => {
    const err = await runStreaming(
      'heroku',
      ['logs', '--tail', '-a', appName('authms')]
    );
    done(err);
  });

  // ── bookingms ────────────────────────────────────────────────
  gulp.task('deploy:dev:push:bookingms', async (done) => {
    const err = await runStreaming(
      'heroku',
      ['container:push', 'web', '-a', appName('bookingms')],
      { cwd: path.join(backendDir, 'bookingms') }
    );
    done(err);
  });

  gulp.task('deploy:dev:release:bookingms', async (done) => {
    const err = await runStreaming(
      'heroku',
      ['container:release', 'web', '-a', appName('bookingms')]
    );
    done(err);
  });

  gulp.task('deploy:dev:logs:bookingms', async (done) => {
    const err = await runStreaming(
      'heroku',
      ['logs', '--tail', '-a', appName('bookingms')]
    );
    done(err);
  });

  // ── frontend ─────────────────────────────────────────────────
  gulp.task('deploy:dev:push:frontend', async (done) => {
    const err = await runStreaming(
      'heroku',
      ['container:push', 'web', '-a', appName('frontend'), '-f', 'Dockerfile.heroku'],
      { cwd: frontendDir }
    );
    done(err);
  });

  gulp.task('deploy:dev:release:frontend', async (done) => {
    const err = await runStreaming(
      'heroku',
      ['container:release', 'web', '-a', appName('frontend')]
    );
    done(err);
  });

  gulp.task('deploy:dev:logs:frontend', async (done) => {
    const err = await runStreaming(
      'heroku',
      ['logs', '--tail', '-a', appName('frontend')]
    );
    done(err);
  });

  // ── 複合タスク ────────────────────────────────────────────────
  gulp.task(
    'deploy:dev',
    gulp.series(
      'deploy:dev:build:backend',
      gulp.parallel(
        gulp.series('deploy:dev:push:authms', 'deploy:dev:release:authms'),
        gulp.series('deploy:dev:push:bookingms', 'deploy:dev:release:bookingms'),
        gulp.series('deploy:dev:push:frontend', 'deploy:dev:release:frontend')
      )
    )
  );

  // ── 初回セットアップ ──────────────────────────────────────────
  // Note: heroku create / heroku config:set は対話的操作が含まれるため
  //       ガイド表示のみ行い、手動実行を促す
  gulp.task('deploy:dev:setup', async (done) => {
    const prefix = getPrefix();
    console.log(`
=== Heroku 開発環境セットアップガイド ===

PREFIX: ${prefix}

1. Heroku ログイン（ブラウザ認証）:
   heroku login
   heroku container:login

2. アプリ作成:
   heroku create ${prefix}-authms --stack container
   heroku create ${prefix}-bookingms --stack container
   heroku create ${prefix}-frontend --stack container

3. authms Config Vars:
   heroku config:set \\
     SPRING_PROFILES_ACTIVE=heroku \\
     JAVA_OPTS="-XX:MaxRAMPercentage=50.0 -XX:ReservedCodeCacheSize=64m -XX:MaxMetaspaceSize=128m" \\
     JWT_SECRET="$(openssl rand -base64 64)" \\
     -a ${prefix}-authms

4. bookingms Config Vars（ローカルバスモード）:
   heroku config:set \\
     SPRING_PROFILES_ACTIVE=heroku \\
     JAVA_OPTS="-XX:MaxRAMPercentage=50.0 -XX:ReservedCodeCacheSize=64m -XX:MaxMetaspaceSize=128m" \\
     -a ${prefix}-bookingms

5. frontend Config Vars:
   heroku config:set \\
     AUTH_API_URL=https://${prefix}-authms.herokuapp.com \\
     BOOKING_API_URL=https://${prefix}-bookingms.herokuapp.com \\
     -a ${prefix}-frontend

6. ビルドとデプロイ:
   npx gulp deploy:dev

詳細: docs/operation/開発環境セットアップ手順書.md
`);
    done();
  });
}
