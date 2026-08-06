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

  /**
   * イメージをビルドして Container Registry に push する。
   *
   * **buildx から直接 push している。** `docker build` + `docker push` では
   * Heroku Container Registry が `error from registry: unsupported` を返す。
   * Docker Desktop の containerd イメージストアが OCI マニフェストで push する一方、
   * Heroku Registry は Docker マニフェスト（schema2）しか受け付けないためである。
   *
   * 対処として次を指定している。
   *   oci-mediatypes=false : Docker メディアタイプを強制する
   *   provenance=false     : attestation を付けない（マニフェストリストになるため）
   *   sbom=false           : 同上
   *
   * これらを外すと「レイヤーは上がるがマニフェストで失敗する」形で落ちる。
   */
  gulp.task('deploy:dev:build-push', (done) => {
    requireDocker('deploy:dev:build-push');
    const tag = devImageTag();
    console.log(`イメージをビルドして push します: ${tag} (platform=${PLATFORM})`);
    // 開発環境は H2 で起動するため、イメージに H2 を含める（ADR-003）。
    // 既定は含めない設定であり、ここで明示的に opt-in している。
    run(
      `docker buildx build --platform ${PLATFORM} ` +
        `--build-arg INCLUDE_H2=true --provenance=false --sbom=false ` +
        `--output "type=image,name=${tag},push=true,oci-mediatypes=false" .`,
      { cwd: appPath() }
    );
    done();
  });

  /** ローカル確認用のビルド（push しない）。 */
  gulp.task('deploy:dev:build', (done) => {
    requireDocker('deploy:dev:build');
    const tag = devImageTag();
    console.log(`イメージをビルドします: ${tag} (platform=${PLATFORM})`);
    run(
      `docker buildx build --platform ${PLATFORM} ` +
        `--build-arg INCLUDE_H2=true --provenance=false --sbom=false ` +
        `--output "type=docker,name=${tag}" .`,
      { cwd: appPath() }
    );
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
    gulp.series('deploy:dev:build-push', 'deploy:dev:release', 'deploy:dev:verify')
  );

  gulp.task(
    'deploy:dev:setup',
    gulp.series('deploy:dev:login', 'deploy:dev:config', 'deploy:dev')
  );

  // ============================================
  // ドキュメントサイト（docs / JIG / jig-erd / manual）
  // ============================================

  /**
   * ドキュメントサイトの Heroku アプリ名。
   * アプリ本体とは別アプリにする（配信するものも更新頻度も異なるため）。
   */
  function docsAppName() {
    const name = process.env.DOCS_HEROKU_APP_NAME;
    if (!name) {
      throw new Error(
        '.env に DOCS_HEROKU_APP_NAME を設定してください。\n' +
          '例: DOCS_HEROKU_APP_NAME=cargo-tracker-take-6-docs'
      );
    }
    return name;
  }

  function docsImageTag() {
    return `${REGISTRY}/${docsAppName()}/web`;
  }

  /**
   * 配信する 4 種類の成果物を生成する。
   *
   * **このイメージは配信するだけで生成は行わない。**
   * JIG は JDK、jig-erd は Docker と Graphviz を必要とするため、
   * 生成はホスト側で行い、成果物だけをイメージに載せる。
   */
  gulp.task(
    'deploy:docs:artifacts',
    gulp.series('mkdocs:build', 'app:jig', 'app:jig-erd', 'manual:build')
  );

  gulp.task('deploy:docs:app:create', (done) => {
    requireHeroku();
    run(`heroku create ${docsAppName()} --stack container`);
    done();
  });

  gulp.task('deploy:docs:build-push', (done) => {
    requireDocker('deploy:docs:build-push');
    const tag = docsImageTag();
    console.log(`ドキュメントサイトをビルドして push します: ${tag}`);
    // push の方式は deploy:dev:build-push と同じ理由（OCI マニフェスト非対応）。
    run(
      `docker buildx build --platform ${PLATFORM} ` +
        `--provenance=false --sbom=false ` +
        `-f ops/docker/docs-site/Dockerfile ` +
        `--output "type=image,name=${tag},push=true,oci-mediatypes=false" .`
    );
    done();
  });

  gulp.task('deploy:docs:release', (done) => {
    requireHeroku();
    run(`heroku container:release web -a ${docsAppName()}`);
    done();
  });

  gulp.task('deploy:docs:verify', (done) => {
    requireHeroku();
    const app = docsAppName();
    const info = execSync(`heroku info -a ${app} --json`, { encoding: 'utf8' });
    const webUrl = JSON.parse(info).app.web_url.replace(/\/$/, '');
    // ポータルと 4 種類の成果物がすべて配信されていることを確認する。
    // **ポータルが表示されただけでは、リンク先が見えているとは限らない。**
    // ポータルからのリンク先をそのまま検証対象にしている。
    const paths = [
      '/healthz',
      '/',
      '/style.css',
      '/docs/',
      '/docs/design/',
      '/docs/requirements/',
      '/docs/adr/',
      '/jig/',
      '/jig-erd/',
      '/manual/',
    ];
    for (const p of paths) {
      const code = execSync(`curl -s -o /dev/null -w '%{http_code}' --max-time 30 ${webUrl}${p}`, {
        encoding: 'utf8',
      }).trim();
      console.log(`  ${code}  ${webUrl}${p}`);
      if (code !== '200') {
        throw new Error(`${webUrl}${p} が ${code} を返しました`);
      }
    }
    console.log('ドキュメントサイトのデプロイを確認しました');
    done();
  });

  gulp.task('deploy:docs:open', (done) => {
    requireHeroku();
    run(`heroku open -a ${docsAppName()}`);
    done();
  });

  gulp.task('deploy:docs:logs', (done) => {
    requireHeroku();
    run(`heroku logs --tail -a ${docsAppName()}`);
    done();
  });

  gulp.task(
    'deploy:docs',
    gulp.series(
      'deploy:docs:artifacts',
      'deploy:docs:build-push',
      'deploy:docs:release',
      'deploy:docs:verify'
    )
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
  deploy:dev:build        ローカル確認用にビルド（push しない）
  deploy:dev:build-push   ビルドして Container Registry に push
  deploy:dev:release      web プロセスをリリース
  deploy:dev              build-push → release → verify を一括実行

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

  push は buildx から直接行っています。docker build + docker push だと
  Docker Desktop の containerd イメージストアが OCI マニフェストで push し、
  Heroku Registry が error from registry: unsupported を返すためです。

  開発環境は H2 インメモリです（dev プロファイル）。
  dyno 再起動でデータは失われます。永続化を前提にしないでください。

ドキュメントサイト:
  deploy:docs:artifacts   4 種類の成果物を生成（mkdocs / JIG / jig-erd / manual）
  deploy:docs:app:create  Heroku アプリを container stack で作成
  deploy:docs:build-push  サイトイメージをビルドして push
  deploy:docs:release     リリース
  deploy:docs:verify      4 種類すべてが 200 を返すことを確認
  deploy:docs:open        ブラウザで開く
  deploy:docs:logs        ログを追跡表示
  deploy:docs             artifacts → build-push → release → verify

  DOCS_HEROKU_APP_NAME    ドキュメントサイトの Heroku アプリ名（必須）

手順書:
  docs/operation/開発環境セットアップ手順書.md
`);
    done();
  });
}
