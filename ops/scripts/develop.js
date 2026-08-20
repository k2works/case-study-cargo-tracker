'use strict';

/**
 * アプリケーション開発タスク（dev:*）
 *
 * 手順は docs/operation/アプリケーション開発環境セットアップ手順書.md に対応する。
 * 環境への操作はここに定義したタスクを使い、使い捨てスクリプトを別途書かない。
 */

import { execSync, spawnSync } from 'child_process';
import { existsSync } from 'fs';
import { dirname, join, resolve } from 'path';
import { cleanDockerEnv, gradleCommand, isDockerAvailable, openUrl } from './shared.js';

const BACKEND_DIR = 'apps/backend';
const FRONTEND_DIR = 'apps/frontend';
const KUSTOMIZE_LOCAL = 'apps/k8s/kustomize/overlays/local';
const KIND_CLUSTER = 'cargo';
const K8S_NAMESPACE = 'cargo';
// Ingress が localhost の 80 番で公開する（apps/k8s/kustomize/base/ingress.yaml）
const K8S_APP_URL = 'http://localhost';

/** 既定で起動するバックエンドサービス。 */
const DEFAULT_SERVICE = 'bookingms';

/** kind クラスタへロードする全イメージ。 */
const SERVICES = [
  'gatewayms',
  'authms',
  'bookingms',
  'routingms',
  'trackingms',
  'handlingms',
  'billingms',
];
const IMAGE_TAG = '0.0.1';

/** 専用データベースを持つサービス。jig-erd の ER 図はこの単位で生成される。 */
const DB_SERVICES = ['authms', 'bookingms', 'routingms', 'trackingms', 'handlingms', 'billingms'];

/**
 * Windows shell に渡す引数を引用する。
 *
 * npm.cmd / gradlew.bat は Windows では shell 経由で実行する必要がある。
 * そのまま spawnSync(command, args, { shell: true }) に渡すと、空白を含む
 * 引数が分割されるため、明示的に command line を組み立てる。
 *
 * @param {string} value 引数
 * @returns {string} 引用済み引数
 */
function quoteWindowsArg(value) {
  return `"${String(value).replace(/^"|"$/g, '').replace(/"/g, '\\"')}"`;
}

/**
 * Windows で実行するコマンド名を補正する。
 *
 * npm / npx は .cmd shim を明示しないと、cwd 配下の node_modules を npm 本体として
 * 誤解決することがある。
 *
 * @param {string} command コマンド
 * @returns {string} Windows shell に渡すコマンド
 */
function windowsCommand(command) {
  const normalized = String(command).replace(/^"|"$/g, '');
  if (['npm', 'npx'].includes(normalized)) {
    return `${normalized}.cmd`;
  }
  return normalized;
}

/**
 * OS 差を吸収して外部コマンドを実行する。
 *
 * @param {string} command コマンド
 * @param {string[]} args 引数
 * @param {object} options spawnSync オプション
 * @returns {import('child_process').SpawnSyncReturns<string | Buffer>} 実行結果
 */
function spawnCommand(command, args, options = {}) {
  if (process.platform !== 'win32') {
    return spawnSync(command, args, options);
  }
  const commandLine = [windowsCommand(command), ...args].map(quoteWindowsArg).join(' ');
  return spawnSync(commandLine, [], { ...options, shell: true });
}

function run(command, args, cwd = '.', extraEnv = {}) {
  const result = spawnCommand(command, args, {
    cwd,
    stdio: 'inherit',
    env: { ...cleanDockerEnv(), ...extraEnv },
  });
  if (result.status !== 0) {
    throw new Error(`${command} ${args.join(' ')} が終了コード ${result.status} で失敗しました`);
  }
}

const gradle = (args) => run(gradleCommand(BACKEND_DIR), args, BACKEND_DIR);

/**
 * npm CLI の実体パスを返す。
 *
 * Windows の npm.cmd は npm-prefix.js により cwd 側の node_modules/npm を
 * npm 本体として探すことがあるため、shim を経由せず node から直接起動する。
 *
 * @returns {string} npm-cli.js のパス
 */
function npmCliPath() {
  return process.env.npm_execpath ?? join(dirname(process.execPath), 'node_modules/npm/bin/npm-cli.js');
}

const npmRun = (args, extraEnv = {}) =>
  run(process.execPath, [npmCliPath(), ...args], FRONTEND_DIR, extraEnv);

/**
 * Ready になっていない Pod 数を取得する。
 *
 * shell pipeline を使うと Windows で grep や /dev/null が解決できないため、
 * kubectl の JSON 出力を Node.js 側で判定する。
 *
 * @returns {number} Ready になっていない Pod 数
 */
function countNotReadyPods() {
  try {
    const output = execSync(`kubectl -n ${K8S_NAMESPACE} get pods -o json`, {
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'ignore'],
    });
    const pods = JSON.parse(output).items ?? [];
    return pods.filter((pod) => {
      const statuses = pod.status?.containerStatuses ?? [];
      return statuses.length === 0 || statuses.some((status) => status.ready !== true);
    }).length;
  } catch {
    return 0;
  }
}

export default function (gulp) {
  // --- バックエンド ---

  gulp.task('dev:backend', (done) => {
    gradle([`:${DEFAULT_SERVICE}:bootRun`]);
    done();
  });

  SERVICES.forEach((service) => {
    gulp.task(`dev:backend:${service}`, (done) => {
      gradle([`:${service}:bootRun`]);
      done();
    });
  });

  gulp.task('dev:backend:build', (done) => {
    gradle(['build', '-x', 'test']);
    done();
  });

  gulp.task('dev:backend:test', (done) => {
    gradle(['test', 'jacocoTestReport']);
    done();
  });

  gulp.task('dev:backend:tdd', (done) => {
    gradle(['test', '--continuous']);
    done();
  });

  gulp.task('dev:backend:check', (done) => {
    gradle(['checkstyleMain', 'spotbugsMain']);
    done();
  });

  // ArchUnit を含む構造的検証はフルテストでしか働かない。
  // Port の追加や ADR 起票を伴う変更では必ずこれを実行する。
  gulp.task('dev:backend:full', (done) => {
    gradle(['build']);
    done();
  });

  // --- フロントエンド ---

  gulp.task('dev:frontend', (done) => {
    npmRun(['run', 'dev'], { VITE_DEMO_LOGIN_ENABLED: 'true' });
    done();
  });

  gulp.task('dev:frontend:build', (done) => {
    npmRun(['run', 'build']);
    done();
  });

  gulp.task('dev:frontend:test', (done) => {
    npmRun(['test']);
    done();
  });

  gulp.task('dev:frontend:tdd', (done) => {
    npmRun(['run', 'test:watch']);
    done();
  });

  gulp.task('dev:frontend:lint', (done) => {
    npmRun(['run', 'lint']);
    done();
  });

  // --- ローカル統合環境（kind + Kustomize） ---

  gulp.task('dev:k8s:cluster:create', (done) => {
    run('kind', ['create', 'cluster', '--config', 'apps/k8s/kind-cluster.yaml']);
    run('kubectl', [
      'apply',
      '-f',
      'https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml',
    ]);
    // Deployment 作成直後は Pod がまだ存在せず、`kubectl wait pod` は
    // 「no matching resources found」で即失敗する。Deployment の
    // ロールアウト完了を待つことで Pod 生成前から待機できる。
    run('kubectl', [
      '-n',
      'ingress-nginx',
      'rollout',
      'status',
      'deployment/ingress-nginx-controller',
      '--timeout=180s',
    ]);
    done();
  });

  gulp.task('dev:k8s:cluster:delete', (done) => {
    run('kind', ['delete', 'cluster', '--name', KIND_CLUSTER]);
    done();
  });

  gulp.task('dev:k8s:images', (done) => {
    gradle(['bootJar', '-x', 'test']);
    SERVICES.forEach((service) => {
      run('docker', ['build', '-t', `cargo-${service}:${IMAGE_TAG}`, service], BACKEND_DIR);
    });
    // ローカル統合は開発環境である。動作確認用ログインの事前入力を有効にする
    run(
      'docker',
      [
        'build',
        '--build-arg',
        'VITE_DEMO_LOGIN_ENABLED=true',
        '-t',
        `cargo-frontend:${IMAGE_TAG}`,
        '.',
      ],
      FRONTEND_DIR,
    );
    [...SERVICES, 'frontend'].forEach((service) => {
      run('kind', [
        'load',
        'docker-image',
        `cargo-${service}:${IMAGE_TAG}`,
        '--name',
        KIND_CLUSTER,
      ]);
    });
    done();
  });

  // 適用前に合成結果を確認する（クラスタには影響しない）
  gulp.task('dev:k8s:diff', (done) => {
    run('kubectl', ['kustomize', KUSTOMIZE_LOCAL]);
    done();
  });

  gulp.task('dev:k8s:apply', (done) => {
    run('kubectl', ['apply', '-k', KUSTOMIZE_LOCAL]);
    done();
  });

  gulp.task('dev:k8s:status', (done) => {
    run('kubectl', ['-n', K8S_NAMESPACE, 'get', 'pods,svc,ingress']);
    done();
  });

  /**
   * ローカル統合環境（kind）の画面をブラウザで開く。
   *
   * Ingress は localhost の 80 番で公開している（apps/k8s/kustomize/base/ingress.yaml）。
   * Pod が Ready でないまま開くと 503 のページを見て「壊れている」と受け取るため、
   * 先に状態を確認する。
   */
  gulp.task('dev:k8s:open', (done) => {
    // STATUS が Running でも READY が 0/1 なら probe を通っておらず、開いても 503 になる。
    // READY 列（n/m）で判定する
    const notReady = countNotReadyPods();

    if (notReady !== 0) {
      console.log(`まだ準備できていない Pod が ${notReady} 件あります（開いても 503 になることがあります）。`);
      console.log('dev:k8s:status で状態を確認してください。');
    }

    openUrl(K8S_APP_URL);
    done();
  });

  gulp.task('dev:k8s:logs', (done) => {
    run('kubectl', ['-n', K8S_NAMESPACE, 'logs', '-l', 'app', '--all-containers', '--tail=100']);
    done();
  });

  gulp.task('dev:k8s:delete', (done) => {
    run('kubectl', ['delete', '-k', KUSTOMIZE_LOCAL]);
    done();
  });

  gulp.task('dev:k8s:up', gulp.series('dev:k8s:images', 'dev:k8s:apply', 'dev:k8s:status'));

  // --- 設計ドキュメント生成（JIG / jig-erd） ---

  /**
   * JIG でコードから設計ドキュメントを生成する。
   *
   * これは「テスト」ではない。生成物と docs/design を突き合わせて、
   * 設計と実装の乖離を人間が確認するための材料である。
   */
  gulp.task('dev:jig', (done) => {
    gradle(['jigReports']);
    console.log('\nJIG ドキュメント:');
    [...SERVICES, 'shared'].forEach((service) => {
      console.log(`  ${BACKEND_DIR}/${service}/build/jig/index.html`);
    });
    done();
  });

  gulp.task('dev:jig:open', (done) => {
    // 既定サービスを開く。他サービスは dev:jig の出力パスから開く。
    const index = resolve(BACKEND_DIR, DEFAULT_SERVICE, 'build/jig/index.html');
    if (!existsSync(index)) {
      throw new Error('JIG ドキュメントが未生成です。先に dev:jig を実行してください。');
    }
    openUrl(`file://${index}`);
    done();
  });

  /**
   * jig-erd で実 DB スキーマから ER 図を生成する。
   *
   * Docker（Testcontainers）と Graphviz が必要。
   * docs/design/data-model.md の ER 図は「設計」、ここで生成されるのは
   * Flyway が構築した「実装」である。
   * Database per Service のため、図はサービスごとに生成される。
   */
  gulp.task('dev:jig-erd', (done) => {
    if (!isDockerAvailable()) {
      throw new Error('jig-erd には Docker が必要です。Docker Desktop を起動してください。');
    }
    try {
      execSync('dot -V', { stdio: 'ignore' });
    } catch {
      throw new Error(
        'jig-erd には Graphviz が必要です。`brew install graphviz` でインストールしてください。',
      );
    }
    gradle(['jigErd']);
    console.log('\nER 図:');
    DB_SERVICES.forEach((service) => {
      console.log(`  ${BACKEND_DIR}/${service}/build/jig-erd/`);
    });
    done();
  });

  // --- ヘルプ ---

  gulp.task('dev:help', (done) => {
    console.log(`
アプリケーション開発タスク

  バックエンド
    dev:backend                既定サービス（${DEFAULT_SERVICE}）を起動
    dev:backend:<service>       個別サービスを起動（${SERVICES.join(', ')}）
    dev:backend:build           ビルド（テストを除く）
    dev:backend:test            テスト + カバレッジ
    dev:backend:tdd             TDD モード（テスト自動再実行）
    dev:backend:check           Checkstyle + SpotBugs
    dev:backend:full            フルビルド（ArchUnit を含む構造的検証）

  フロントエンド
    dev:frontend                開発サーバー起動（port 3000）
    dev:frontend:build          ビルド
    dev:frontend:test           テスト
    dev:frontend:tdd            テスト watch モード
    dev:frontend:lint           ESLint / oxlint

  ローカル統合環境（kind + Kustomize）
    dev:k8s:cluster:create      kind クラスタ作成 + Ingress Controller 導入
    dev:k8s:cluster:delete      kind クラスタ削除
    dev:k8s:images              jar ビルド → イメージ作成 → kind へロード
    dev:k8s:diff                Kustomize の合成結果を表示（適用しない）
    dev:k8s:apply               overlays/local を適用
    dev:k8s:up                  images → apply → status を一括実行
    dev:k8s:status              Pod / Service / Ingress の状態
    dev:k8s:logs                全サービスの直近ログ
    dev:k8s:delete              デプロイを削除（クラスタは残す）

  設計ドキュメント生成
    dev:jig                     JIG でコードから設計ドキュメントを生成（全サービス）
    dev:k8s:open                ローカル統合環境（kind）の画面をブラウザで開く
    dev:jig:open                JIG ドキュメント（${DEFAULT_SERVICE}）をブラウザで開く
    dev:jig-erd                 jig-erd で実スキーマから ER 図を生成（Docker + Graphviz 必要）

  docs/design は「こう設計した」、JIG / jig-erd の出力は「こう実装されている」を示す。
  両者を突き合わせて設計と実装の乖離を検出する。
`);
    done();
  });
}
