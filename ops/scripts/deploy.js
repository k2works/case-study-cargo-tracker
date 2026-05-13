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
 *   - DEV_HEROKU_APP_PREFIX   : Heroku アプリ名のプレフィックス（例: cargo-tracker-4）
 *   - DEV_DOCKER_PLATFORM     : Docker プラットフォーム（デフォルト: linux/amd64）
 *
 * @param {import('gulp').Gulp} gulp
 */

import { spawn, execSync } from 'child_process';
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

function getAppDomain(service) {
  const name = appName(service);
  try {
    const result = execSync(`heroku domains -a ${name} --json`, { encoding: 'utf8' });
    const domains = JSON.parse(result);
    const herokuDomain = domains.find(d => d.hostname && d.hostname.includes('herokuapp.com'));
    return herokuDomain ? `https://${herokuDomain.hostname}` : `https://${name}.herokuapp.com`;
  } catch {
    return `https://${name}.herokuapp.com`;
  }
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
      [':authms:bootJar', ':bookingms:bootJar', ':gatewayms:bootJar'],
      { cwd: backendDir }
    );
    done(err);
  });

  // ── authms ──────────────────────────────────────────────────
  gulp.task('deploy:dev:push:authms', async (done) => {
    const app = appName('authms');
    const image = `registry.heroku.com/${app}/web`;
    // Dockerfile.heroku はローカルビルド済み JAR のみコピーする軽量イメージ
    const buildErr = await runStreaming(
      'docker',
      ['build', '--platform', getPlatform(), '--provenance=false', '-f', 'Dockerfile.heroku', '-t', image, '.'],
      { cwd: path.join(backendDir, 'authms') }
    );
    if (buildErr) { done(buildErr); return; }
    const pushErr = await runStreaming('docker', ['push', image]);
    done(pushErr);
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
    const app = appName('bookingms');
    const image = `registry.heroku.com/${app}/web`;
    const buildErr = await runStreaming(
      'docker',
      ['build', '--platform', getPlatform(), '--provenance=false', '-f', 'Dockerfile.heroku', '-t', image, '.'],
      { cwd: path.join(backendDir, 'bookingms') }
    );
    if (buildErr) { done(buildErr); return; }
    const pushErr = await runStreaming('docker', ['push', image]);
    done(pushErr);
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

  // ── gatewayms ────────────────────────────────────────────────
  gulp.task('deploy:dev:push:gatewayms', async (done) => {
    const app = appName('gatewayms');
    const image = `registry.heroku.com/${app}/web`;
    const buildErr = await runStreaming(
      'docker',
      ['build', '--platform', getPlatform(), '--provenance=false', '-f', 'Dockerfile.heroku', '-t', image, '.'],
      { cwd: path.join(backendDir, 'gatewayms') }
    );
    if (buildErr) { done(buildErr); return; }
    const pushErr = await runStreaming('docker', ['push', image]);
    done(pushErr);
  });

  gulp.task('deploy:dev:release:gatewayms', async (done) => {
    const err = await runStreaming(
      'heroku',
      ['container:release', 'web', '-a', appName('gatewayms')]
    );
    done(err);
  });

  gulp.task('deploy:dev:logs:gatewayms', async (done) => {
    const err = await runStreaming(
      'heroku',
      ['logs', '--tail', '-a', appName('gatewayms')]
    );
    done(err);
  });

  // ── frontend ─────────────────────────────────────────────────
  gulp.task('deploy:dev:push:frontend', async (done) => {
    const app = appName('frontend');
    const image = `registry.heroku.com/${app}/web`;
    // heroku container:push は -f 非対応のため docker build/push で代替
    const buildErr = await runStreaming(
      'docker',
      ['build', '--platform', getPlatform(), '--provenance=false', '-f', 'Dockerfile.heroku', '-t', image, '.'],
      { cwd: frontendDir }
    );
    if (buildErr) { done(buildErr); return; }
    const pushErr = await runStreaming('docker', ['push', image]);
    done(pushErr);
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

  // ── Config Vars 設定 ─────────────────────────────────────────
  gulp.task('deploy:dev:config:authms', async (done) => {
    const jwtSecret = process.env.JWT_SECRET;
    if (!jwtSecret) {
      done(new Error('JWT_SECRET が .env に設定されていません'));
      return;
    }
    const err = await runStreaming('heroku', [
      'config:set',
      'SPRING_PROFILES_ACTIVE=heroku',
      'JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=50.0 -XX:ReservedCodeCacheSize=64m -XX:MaxMetaspaceSize=128m -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Tokyo',
      `JWT_SECRET=${jwtSecret}`,
      'JWT_ISSUER=case-study-cargo-tracker',
      '-a', appName('authms'),
    ]);
    done(err);
  });

  gulp.task('deploy:dev:config:bookingms', async (done) => {
    const err = await runStreaming('heroku', [
      'config:set',
      'SPRING_PROFILES_ACTIVE=heroku',
      'JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=50.0 -XX:ReservedCodeCacheSize=64m -XX:MaxMetaspaceSize=128m -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Tokyo',
      '-a', appName('bookingms'),
    ]);
    done(err);
  });

  gulp.task('deploy:dev:config:gatewayms', async (done) => {
    const jwtSecret = process.env.JWT_SECRET;
    if (!jwtSecret) {
      done(new Error('JWT_SECRET が .env に設定されていません'));
      return;
    }
    const prefix = getPrefix();
    const err = await runStreaming('heroku', [
      'config:set',
      'SPRING_PROFILES_ACTIVE=heroku',
      'JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=50.0 -XX:ReservedCodeCacheSize=64m -XX:MaxMetaspaceSize=128m -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Tokyo',
      `JWT_SECRET=${jwtSecret}`,
      `AUTHMS_URL=${getAppDomain('authms')}`,
      `BOOKINGMS_URL=${getAppDomain('bookingms')}`,
      '-a', appName('gatewayms'),
    ]);
    done(err);
  });

  gulp.task('deploy:dev:config:frontend', async (done) => {
    const gatewayUrl = getAppDomain('gatewayms');
    const gatewayHost = gatewayUrl.replace(/^https?:\/\//, '');
    const err = await runStreaming('heroku', [
      'config:set',
      `GATEWAY_URL=${gatewayUrl}`,
      `GATEWAY_HOST=${gatewayHost}`,
      '-a', appName('frontend'),
    ]);
    done(err);
  });

  gulp.task(
    'deploy:dev:config',
    gulp.parallel(
      'deploy:dev:config:authms',
      'deploy:dev:config:bookingms',
      'deploy:dev:config:gatewayms',
      'deploy:dev:config:frontend'
    )
  );

  // ── Heroku Container Registry ログイン ───────────────────────
  gulp.task('deploy:dev:login', async (done) => {
    const err = await runStreaming('heroku', ['container:login']);
    done(err);
  });

  // ── 複合タスク ────────────────────────────────────────────────
  gulp.task(
    'deploy:dev',
    gulp.series(
      'deploy:dev:login',
      'deploy:dev:build:backend',
      gulp.parallel(
        gulp.series('deploy:dev:push:authms', 'deploy:dev:release:authms'),
        gulp.series('deploy:dev:push:bookingms', 'deploy:dev:release:bookingms'),
        gulp.series('deploy:dev:push:gatewayms', 'deploy:dev:release:gatewayms'),
        gulp.series('deploy:dev:push:frontend', 'deploy:dev:release:frontend')
      )
    )
  );

  // ── アプリを開く ─────────────────────────────────────────────
  gulp.task('deploy:dev:open', async (done) => {
    const prefix = getPrefix();
    const apps = ['authms', 'bookingms', 'gatewayms', 'frontend'];
    for (const svc of apps) {
      const err = await runStreaming('heroku', ['open', '-a', `${prefix}-${svc}`]);
      if (err) { done(err); return; }
    }
    done();
  });

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

3. .env に JWT_SECRET を設定:
   JWT_SECRET="$(openssl rand -base64 64)"

4. Config Vars を一括設定（.env の JWT_SECRET を使用）:
   npx gulp deploy:dev:config

5. ビルドとデプロイ:
   npx gulp deploy:dev

詳細: docs/operation/開発環境セットアップ手順書.md
`);
    done();
  });
}
