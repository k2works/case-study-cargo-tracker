'use strict';

import { execSync } from 'child_process';

// ============================================
// Heroku 開発環境デプロイ（開発環境セットアップ手順書 参照）
// ============================================

const APP_DIR = 'apps/cargo-tracker';
const DOCKERFILE = 'src/CargoTracker.Web/Dockerfile';

/**
 * Heroku デプロイ設定を .env から取得
 * @returns {{ appName: string, platform: string, image: string }}
 */
function getHerokuConfig() {
  const appName = process.env.DEV_HEROKU_APP_NAME;
  const platform = process.env.DEV_DOCKER_PLATFORM || 'linux/amd64';

  if (!appName) {
    throw new Error('DEV_HEROKU_APP_NAME を .env に設定してください');
  }

  return { appName, platform, image: `registry.heroku.com/${appName}/web` };
}

/**
 * コマンドを実行（ログ付き・出力は端末へ）
 * @param {string} command
 * @param {object} [options]
 */
function run(command, options = {}) {
  console.log(`$ ${command}`);
  execSync(command, { stdio: 'inherit', ...options });
}

export default function (gulp) {
  // Heroku CLI / Container Registry へのログイン
  gulp.task('deploy:dev:login', (done) => {
    try {
      run('heroku login');
      run('heroku container:login');
      done();
    } catch (error) {
      done(error);
    }
  });

  // Heroku アプリの作成（container stack）
  gulp.task('deploy:dev:app:create', (done) => {
    try {
      const { appName } = getHerokuConfig();
      run(`heroku create ${appName} --stack container`);
      done();
    } catch (error) {
      done(error);
    }
  });

  // イメージのビルド（Heroku Runtime は x86_64 のみのため linux/amd64 固定）
  gulp.task('deploy:dev:build', (done) => {
    try {
      const { platform, image } = getHerokuConfig();
      // --provenance=false: Heroku Registry は provenance アテステーション付き
      // マニフェストを受け付けない（push 時に "error from registry: unsupported"）
      run(
        `docker build --platform ${platform} --provenance=false -t ${image} -f ${DOCKERFILE} .`,
        { cwd: APP_DIR }
      );
      done();
    } catch (error) {
      done(error);
    }
  });

  // イメージの push
  // 注意: Docker Desktop の containerd イメージストアでは `docker push` が
  // OCI マニフェストを送信し、Heroku Registry が "unsupported" で拒否する。
  // buildx の直接 push（oci-mediatypes=false）で Docker schema2 形式に固定する。
  gulp.task('deploy:dev:push', (done) => {
    try {
      const { platform, image } = getHerokuConfig();
      run(
        `docker buildx build --platform ${platform} --provenance=false ` +
          `--output type=image,name=${image},oci-mediatypes=false,push=true ` +
          `-f ${DOCKERFILE} .`,
        { cwd: APP_DIR }
      );
      done();
    } catch (error) {
      done(error);
    }
  });

  // リリース（push 済みイメージを web プロセスへ）
  gulp.task('deploy:dev:release', (done) => {
    try {
      const { appName } = getHerokuConfig();
      run(`heroku container:release web -a ${appName}`);
      done();
    } catch (error) {
      done(error);
    }
  });

  // ブラウザで開く
  gulp.task('deploy:dev:open', (done) => {
    try {
      const { appName } = getHerokuConfig();
      run(`heroku open -a ${appName}`);
      done();
    } catch (error) {
      done(error);
    }
  });

  // ログのリアルタイム表示
  gulp.task('deploy:dev:logs', (done) => {
    try {
      const { appName } = getHerokuConfig();
      run(`heroku logs --tail -a ${appName}`);
      done();
    } catch (error) {
      done(error);
    }
  });

  // ステータス確認（dyno / リリース情報）
  gulp.task('deploy:dev:status', (done) => {
    try {
      const { appName } = getHerokuConfig();
      run(`heroku ps -a ${appName}`);
      run(`heroku releases -a ${appName} --num 5`);
      done();
    } catch (error) {
      done(error);
    }
  });

  // シードデータ有効化: Seed__Enabled=true を設定（設定変更で dyno が再起動しシード投入される）
  gulp.task('deploy:dev:seed', (done) => {
    try {
      const { appName } = getHerokuConfig();
      run(`heroku config:set Seed__Enabled=true -a ${appName}`);
      done();
    } catch (error) {
      done(error);
    }
  });

  // 一括デプロイ: build → push → release
  gulp.task('deploy:dev', gulp.series('deploy:dev:build', 'deploy:dev:push', 'deploy:dev:release'));

  // 初回セットアップ: login → app:create → build → push → release
  gulp.task(
    'deploy:dev:setup',
    gulp.series(
      'deploy:dev:login',
      'deploy:dev:app:create',
      'deploy:dev:build',
      'deploy:dev:push',
      'deploy:dev:release'
    )
  );

  // ヘルプ
  gulp.task('deploy:dev:help', (done) => {
    console.log(`
=== Heroku 開発環境デプロイタスク ===

前提: .env に DEV_HEROKU_APP_NAME を設定（任意: DEV_DOCKER_PLATFORM、デフォルト linux/amd64）

  deploy:dev              一括デプロイ（build → push → release）
  deploy:dev:setup        初回セットアップ（login → app:create → build → push → release）
  deploy:dev:login        heroku login + container:login
  deploy:dev:app:create   Heroku アプリ作成（--stack container）
  deploy:dev:build        イメージビルド（linux/amd64）
  deploy:dev:push         registry.heroku.com へ push
  deploy:dev:release      web プロセスとしてリリース
  deploy:dev:open         ブラウザでアプリを開く
  deploy:dev:logs         ログのリアルタイム表示
  deploy:dev:status       dyno / リリース状態の確認
  deploy:dev:seed         シードデータ有効化（Seed__Enabled=true・再起動でデモデータ投入）

詳細: docs/operation/開発環境セットアップ手順書.md
`);
    done();
  });
}
