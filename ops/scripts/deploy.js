'use strict';

import path from 'path';
import { execSync } from 'child_process';
import { cleanDockerEnv, isDockerAvailable, openUrl } from './shared.js';

// ============================================
// 設定
// ============================================

/** アプリケーションのディレクトリ */
const APP_DIR = process.env.APP_DIR || 'apps/cargo-tracker';

/** Heroku Container Registry のホスト */
const REGISTRY = 'registry.heroku.com';

/**
 * ビルドプラットフォーム。
 *
 * Heroku Container Runtime は x86_64 イメージのみをサポートする。
 * Apple Silicon で既定のまま build すると arm64 イメージになり、
 * push は通るが release 時に unsupported architecture で失敗する。
 */
const PLATFORM = process.env.DEV_DOCKER_PLATFORM || 'linux/amd64';

/** 開発環境の Heroku アプリ名 */
function devAppName() {
  const name = process.env.DEV_HEROKU_APP_NAME;
  if (!name) {
    throw new Error(
      '.env に DEV_HEROKU_APP_NAME を設定してください。\n' +
        '例: DEV_HEROKU_APP_NAME=cargo-tracker-dev'
    );
  }
  return name;
}

/** 開発環境の web イメージタグ */
function devImageTag() {
  return `${REGISTRY}/${devAppName()}/web`;
}

/** アプリケーションディレクトリの絶対パス */
function appPath() {
  return path.join(process.cwd(), APP_DIR);
}

/** シェルコマンドを実行する */
function run(cmd, options = {}) {
  execSync(cmd, { stdio: 'inherit', env: cleanDockerEnv(), ...options });
}

/** Heroku CLI が使えることを確認する */
function requireHeroku() {
  try {
    execSync('heroku --version', { stdio: 'ignore' });
  } catch {
    throw new Error(
      'Heroku CLI が見つかりません。https://devcenter.heroku.com/articles/heroku-cli からインストールしてください。'
    );
  }
}

/** Docker が使えることを確認する */
function requireDocker(taskName) {
  if (!isDockerAvailable()) {
    throw new Error(`${taskName} には Docker が必要です。Docker Desktop を起動してください。`);
  }
}

// ============================================
// タスク定義
// ============================================

export default function deployTasks(gulp) {
  // --- 準備 ---

  gulp.task('deploy:dev:login', (done) => {
    requireHeroku();
    requireDocker('deploy:dev:login');
    run('heroku login');
    run('heroku container:login');
    done();
  });

  gulp.task('deploy:dev:app:create', (done) => {
    requireHeroku();
    const app = devAppName();
    // container stack で作成する。Docker イメージを明示的に管理するため
    // buildpack ベースではなく container stack を採用している。
    run(`heroku create ${app} --stack container`);
    done();
  });

  gulp.task('deploy:dev:app:stack', (done) => {
    requireHeroku();
    run(`heroku stack:set container -a ${devAppName()}`);
    done();
  });

  gulp.task('deploy:dev:config', (done) => {
    requireHeroku();
    const app = devAppName();
    // SPRING_PROFILES_ACTIVE は Dockerfile の ENV で dev を指定しているが、
    // Config Vars で明示しておくと heroku config で構成が読める。
    run(
      `heroku config:set SPRING_PROFILES_ACTIVE=dev ` +
        `JAVA_OPTS="-XX:MaxRAMPercentage=75.0" -a ${app}`
    );
    done();
  });

  // --- ビルドと push ---

  gulp.task('deploy:dev:build', (done) => {
    requireDocker('deploy:dev:build');
    const tag = devImageTag();
    console.log(`イメージをビルドします: ${tag} (platform=${PLATFORM})`);
    // 開発環境は H2 で起動するため、イメージに H2 を含める（ADR-003）。
    // 既定は含めない設定であり、ここで明示的に opt-in している。
    run(`docker build --platform ${PLATFORM} --build-arg INCLUDE_H2=true -t ${tag} .`, {
      cwd: appPath(),
    });
    done();
  });

  gulp.task('deploy:dev:push', (done) => {
    requireDocker('deploy:dev:push');
    run(`docker push ${devImageTag()}`);
    done();
  });

  gulp.task('deploy:dev:release', (done) => {
    requireHeroku();
    run(`heroku container:release web -a ${devAppName()}`);
    done();
  });

  // --- 確認 ---

  gulp.task('deploy:dev:open', (done) => {
    requireHeroku();
    run(`heroku open -a ${devAppName()}`);
    done();
  });

  gulp.task('deploy:dev:logs', (done) => {
    requireHeroku();
    run(`heroku logs --tail -a ${devAppName()}`);
    done();
  });

  gulp.task('deploy:dev:status', (done) => {
    requireHeroku();
    const app = devAppName();
    run(`heroku ps -a ${app}`);
    run(`heroku releases -a ${app} --num 5`);
    done();
  });

  /**
   * デプロイ後の疎通確認。
   *
   * **release が成功したことと、アプリが動いていることは別である。**
   * ヘルスチェックを叩いて UP を確認するまでがデプロイである。
   */
  gulp.task('deploy:dev:verify', (done) => {
    requireHeroku();
    const app = devAppName();
    const url = `https://${app}-*.herokuapp.com/actuator/health`;
    console.log('ヘルスチェックを確認します');
    try {
      const info = execSync(`heroku info -a ${app} --json`, { encoding: 'utf8' });
      const webUrl = JSON.parse(info).app.web_url.replace(/\/$/, '');
      const body = execSync(`curl -sf --max-time 30 ${webUrl}/actuator/health`, {
        encoding: 'utf8',
      });
      console.log(`  ${webUrl}/actuator/health -> ${body}`);
      if (!body.includes('"status":"UP"')) {
        throw new Error(`ヘルスチェックが UP ではありません: ${body}`);
      }
      console.log('デプロイを確認しました');
    } catch (e) {
      throw new Error(
        `ヘルスチェックに失敗しました（${url}）。\n` +
          `heroku logs --tail -a ${app} で原因を確認してください。\n${e.message}`
      );
    }
    done();
  });

  // --- 一括実行 ---

  gulp.task(
    'deploy:dev',
    gulp.series('deploy:dev:build', 'deploy:dev:push', 'deploy:dev:release', 'deploy:dev:verify')
  );

  gulp.task(
    'deploy:dev:setup',
    gulp.series('deploy:dev:login', 'deploy:dev:config', 'deploy:dev')
  );

  // --- ヘルプ ---

  gulp.task('deploy:dev:help', (done) => {
    console.log(`
開発環境デプロイタスク（Heroku Container Registry）

準備:
  deploy:dev:login        Heroku と Container Registry にログイン
  deploy:dev:app:create   Heroku アプリを container stack で作成
  deploy:dev:app:stack    既存アプリの stack を container に変更
  deploy:dev:config       Config Vars を設定

ビルドとリリース:
  deploy:dev:build        Docker イメージをビルド（platform=${PLATFORM}）
  deploy:dev:push         Container Registry に push
  deploy:dev:release      web プロセスをリリース
  deploy:dev              build → push → release → verify を一括実行

確認:
  deploy:dev:verify       ヘルスチェックで疎通を確認
  deploy:dev:open         アプリをブラウザで開く
  deploy:dev:logs         ログを追跡表示
  deploy:dev:status       dyno とリリース履歴を表示

初回セットアップ:
  deploy:dev:setup        login → config → build → push → release → verify

環境変数（.env に設定）:
  DEV_HEROKU_APP_NAME     Heroku アプリ名（必須）
  DEV_DOCKER_PLATFORM     ビルドプラットフォーム（デフォルト: linux/amd64）
  APP_DIR                 アプリのディレクトリ（デフォルト: apps/cargo-tracker）

注意:
  Heroku Container Runtime は x86_64 のみをサポートします。
  Apple Silicon で既定のままビルドすると arm64 イメージになり、
  push は通っても release で unsupported architecture になります。

  開発環境は H2 インメモリです（dev プロファイル）。
  dyno 再起動でデータは失われます。永続化を前提にしないでください。

手順書:
  docs/operation/開発環境セットアップ手順書.md
`);
    done();
  });
}
