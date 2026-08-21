'use strict';

/**
 * アプリケーション開発タスク（dev:*）
 *
 * 手順は docs/operation/アプリケーション開発環境セットアップ手順書.md に対応する。
 * 環境への操作はここに定義したタスクを使い、使い捨てスクリプトを別途書かない。
 */

import { execSync, spawnSync } from 'child_process';
import { copyFileSync, cpSync, existsSync, mkdirSync, rmSync } from 'fs';
import { dirname, join, resolve } from 'path';
import { createInterface } from 'readline/promises';
import { cleanDockerEnv, gradleCommand, isDockerAvailable, openUrl } from './shared.js';

const BACKEND_DIR = 'apps/backend';
const FRONTEND_DIR = 'apps/frontend';
const KUSTOMIZE_LOCAL = 'apps/k8s/kustomize/overlays/local';
const KIND_CLUSTER = 'cargo';
const K8S_NAMESPACE = 'cargo';
/**
 * kubectl の接続先。
 *
 * 明示しないと「そのとき選ばれているコンテキスト」に対して実行される。docker-desktop の
 * Kubernetes を併用していると、同じ名前空間が両方に存在し、**別のクラスタを操作していても
 * 何も言わずに成功する**。ローカル統合環境が動かない原因として最も気づきにくい。
 */
const K8S_CONTEXT = `kind-${KIND_CLUSTER}`;
// Ingress が localhost の 80 番で公開する（apps/k8s/kustomize/base/ingress.yaml）
const K8S_APP_URL = 'http://localhost';
const K8S_DOCS_PORTAL_URL = `${K8S_APP_URL}/docs-portal/`;

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
const DEFAULT_IMAGE_TAG = '0.0.1';

/** 専用データベースを持つサービス。jig-erd の ER 図はこの単位で生成される。 */
const DB_SERVICES = ['authms', 'bookingms', 'routingms', 'trackingms', 'handlingms', 'billingms'];

/** アプリケーションとしてロールアウト対象にする Deployment。 */
const K8S_DEPLOYMENTS = [...SERVICES, 'frontend', 'www'];
let promptedReleaseImageTag;

const JIG_SERVICES = [...SERVICES, 'shared'];

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

const gradle = (args, extraEnv = {}) => run(gradleCommand(BACKEND_DIR), args, BACKEND_DIR, extraEnv);

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
 * CLI オプション値を取得する。
 *
 * `--tag 20260820-001` と `--tag=20260820-001` の両方に対応する。
 *
 * @param {string[]} names オプション名
 * @returns {string | undefined} オプション値
 */
function cliOptionValue(names) {
  for (const name of names) {
    const index = process.argv.indexOf(name);
    if (index !== -1 && process.argv[index + 1] && !process.argv[index + 1].startsWith('--')) {
      return process.argv[index + 1];
    }
    const prefix = `${name}=`;
    const matched = process.argv.find((arg) => arg.startsWith(prefix));
    if (matched) {
      return matched.slice(prefix.length);
    }
  }
  return undefined;
}

/**
 * dev:k8s 系タスクで明示指定された Docker イメージタグを返す。
 *
 * @returns {string | undefined} Docker イメージタグ
 */
function explicitImageTag() {
  return (
    cliOptionValue(['--tag', '--image-tag']) ??
    process.env.DEV_K8S_IMAGE_TAG ??
    process.env.IMAGE_TAG ??
    process.env.npm_config_tag ??
    process.env.npm_config_image_tag ??
    promptedReleaseImageTag
  );
}

/**
 * Docker イメージタグを検証する。
 *
 * @param {string} tag Docker イメージタグ
 */
function assertValidImageTag(tag) {
  if (!/^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$/.test(tag)) {
    throw new Error(
      `Docker イメージタグが不正です: ${tag}。英数字、_、.、- を使い、128 文字以内にしてください。`,
    );
  }
}

/**
 * dev:k8s 系タスクで使う Docker イメージタグを返す。
 *
 * @returns {string} Docker イメージタグ
 */
function imageTag() {
  const tag = explicitImageTag() ?? DEFAULT_IMAGE_TAG;
  assertValidImageTag(tag);
  return tag;
}

/**
 * dev:k8s:release で使う Docker イメージタグをプロンプト入力する。
 *
 * @returns {Promise<void>}
 */
async function promptReleaseImageTag() {
  if (explicitImageTag()) {
    return;
  }
  if (!process.stdin.isTTY || !process.stdout.isTTY) {
    console.log(`リリース番号が未指定のため、既定のタグ ${DEFAULT_IMAGE_TAG} を使います。`);
    return;
  }

  const rl = createInterface({ input: process.stdin, output: process.stdout });
  try {
    const answer = await rl.question(`リリース番号（Docker image tag）を入力してください [${DEFAULT_IMAGE_TAG}]: `);
    promptedReleaseImageTag = answer.trim() || DEFAULT_IMAGE_TAG;
    assertValidImageTag(promptedReleaseImageTag);
    console.log(`リリース番号: ${promptedReleaseImageTag}`);
  } finally {
    rl.close();
  }
}

/**
 * サービスの Docker イメージ名を返す。
 *
 * @param {string} service サービス名
 * @param {string} tag Docker イメージタグ
 * @returns {string} Docker イメージ名
 */
function dockerImage(service, tag = imageTag()) {
  return `cargo-${service}:${tag}`;
}

/**
 * Docker イメージがローカルに存在するか判定する。
 *
 * @param {string} image Docker イメージ名
 * @returns {boolean} 存在する場合 true
 */
function dockerImageExists(image) {
  const result = spawnCommand('docker', ['image', 'inspect', image], {
    stdio: 'ignore',
    env: cleanDockerEnv(),
  });
  return result.status === 0;
}

/**
 * release 用イメージタグが既存イメージと重複していないことを確認する。
 */
function assertUniqueReleaseImageTag() {
  if (!isDockerAvailable()) {
    throw new Error('タグ重複確認には Docker が必要です。Docker Desktop を起動してください。');
  }
  const tag = imageTag();
  const existingImages = K8S_DEPLOYMENTS.map((service) => dockerImage(service, tag)).filter((image) =>
    dockerImageExists(image),
  );
  if (existingImages.length !== 0) {
    throw new Error(
      [
        `指定されたタグは既存イメージと重複しています: ${tag}`,
        ...existingImages.map((image) => `  - ${image}`),
        '別のタグを指定してください。例: npx gulp dev:k8s:release --tag 20260820-001',
      ].join('\n'),
    );
  }
}

/**
 * Testcontainers が Docker Desktop の Linux Engine を見つけるための環境変数を返す。
 *
 * Windows の ~/.testcontainers.properties に古い npipe URL が残っていると、
 * docker CLI は動いても Java/Testcontainers 側だけ Docker environment の検出に失敗する。
 *
 * @returns {Record<string, string>} Testcontainers 用の追加環境変数
 */
function testcontainersDockerEnv() {
  if (process.platform !== 'win32') {
    return {};
  }
  return {
    DOCKER_HOST: process.env.DOCKER_HOST ?? 'npipe:////./pipe/dockerDesktopLinuxEngine',
  };
}

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
    const output = execSync(`kubectl --context ${K8S_CONTEXT} -n ${K8S_NAMESPACE} get pods -o json`, {
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

/**
 * ディレクトリを作り直してコピーする。
 *
 * @param {string} from コピー元
 * @param {string} to コピー先
 */
function syncDirectory(from, to) {
  if (!existsSync(from)) {
    throw new Error(`生成物が見つかりません: ${from}`);
  }
  rmSync(to, { recursive: true, force: true });
  cpSync(from, to, { recursive: true });
}

/**
 * JIG 生成物をドキュメントポータル配下へ同期する。
 */
function syncJigToPortal() {
  const outDir = resolve('apps/www/jig');
  rmSync(outDir, { recursive: true, force: true });
  mkdirSync(outDir, { recursive: true });
  JIG_SERVICES.forEach((service) => {
    syncDirectory(resolve(BACKEND_DIR, service, 'build/jig'), join(outDir, service));
  });
  copyFileSync(resolve('ops/docker/docs-site/jig-index.html'), join(outDir, 'index.html'));
}

/**
 * jig-erd 生成物をドキュメントポータル配下へ同期する。
 */
function syncJigErdToPortal() {
  const outDir = resolve('apps/www/jig-erd');
  rmSync(outDir, { recursive: true, force: true });
  mkdirSync(outDir, { recursive: true });
  DB_SERVICES.forEach((service) => {
    syncDirectory(resolve(BACKEND_DIR, service, 'build/jig-erd'), join(outDir, service));
  });
  copyFileSync(resolve('ops/docker/docs-site/jig-erd-index.html'), join(outDir, 'index.html'));
}

/**
 * Kubernetes Deployment リソース名を返す。
 *
 * @param {string} service サービス名
 * @returns {string} kubectl に渡す Deployment 名
 */
function k8sDeployment(service) {
  return `deployment/${service}`;
}

/**
 * アプリケーション Deployment に指定タグのイメージを設定する。
 *
 * @param {string} tag Docker イメージタグ
 */
function setApplicationDeploymentImages(tag = imageTag()) {
  K8S_DEPLOYMENTS.forEach((service) => {
    run('kubectl', [
      '--context', K8S_CONTEXT,
      '-n',
      K8S_NAMESPACE,
      'set',
      'image',
      k8sDeployment(service),
      `${service}=${dockerImage(service, tag)}`,
    ]);
  });
}

/**
 * アプリケーション Deployment を再起動する。
 */
function restartApplicationDeployments() {
  K8S_DEPLOYMENTS.forEach((service) => {
    run('kubectl', ['--context', K8S_CONTEXT, '-n', K8S_NAMESPACE, 'rollout', 'restart', k8sDeployment(service)]);
  });
}

/**
 * アプリケーション Deployment のロールアウト完了を待つ。
 */
function waitApplicationRollouts() {
  K8S_DEPLOYMENTS.forEach((service) => {
    run('kubectl', [
      '--context', K8S_CONTEXT,
      '-n',
      K8S_NAMESPACE,
      'rollout',
      'status',
      k8sDeployment(service),
      '--timeout=180s',
    ]);
  });
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
      '--context', K8S_CONTEXT,
      'apply',
      '-f',
      'https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml',
    ]);
    // Deployment 作成直後は Pod がまだ存在せず、`kubectl wait pod` は
    // 「no matching resources found」で即失敗する。Deployment の
    // ロールアウト完了を待つことで Pod 生成前から待機できる。
    run('kubectl', [
      '--context', K8S_CONTEXT,
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
    const tag = imageTag();
    gradle(['bootJar', '-x', 'test']);
    SERVICES.forEach((service) => {
      run('docker', ['build', '-t', dockerImage(service, tag), service], BACKEND_DIR);
    });
    // ローカル統合は開発環境である。動作確認用ログインの事前入力を有効にする
    run(
      'docker',
      [
        'build',
        '--build-arg',
        'VITE_DEMO_LOGIN_ENABLED=true',
        '-t',
        dockerImage('frontend', tag),
        '.',
      ],
      FRONTEND_DIR,
    );
    run('docker', ['build', '-t', dockerImage('www', tag), '.'], 'apps/www');
    K8S_DEPLOYMENTS.forEach((service) => {
      run('kind', [
        'load',
        'docker-image',
        dockerImage(service, tag),
        '--name',
        KIND_CLUSTER,
      ]);
    });
    done();
  });

  // 適用前に合成結果を確認する（クラスタには影響しない）
  gulp.task('dev:k8s:diff', (done) => {
    run('kubectl', ['--context', K8S_CONTEXT, 'kustomize', KUSTOMIZE_LOCAL]);
    done();
  });

  gulp.task('dev:k8s:apply', (done) => {
    run('kubectl', ['--context', K8S_CONTEXT, 'apply', '-k', KUSTOMIZE_LOCAL]);
    done();
  });

  gulp.task('dev:k8s:status', (done) => {
    run('kubectl', ['--context', K8S_CONTEXT, '-n', K8S_NAMESPACE, 'get', 'pods,svc,ingress']);
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

  gulp.task('dev:k8s:docs:open', (done) => {
    const notReady = countNotReadyPods();

    if (notReady !== 0) {
      console.log(`まだ準備できていない Pod が ${notReady} 件あります（開いても 503 になることがあります）。`);
      console.log('dev:k8s:status で状態を確認してください。');
    }

    openUrl(K8S_DOCS_PORTAL_URL);
    done();
  });

  gulp.task('dev:k8s:logs', (done) => {
    run('kubectl', ['--context', K8S_CONTEXT, '-n', K8S_NAMESPACE, 'logs', '-l', 'app', '--all-containers', '--tail=100']);
    done();
  });

  gulp.task('dev:k8s:delete', (done) => {
    run('kubectl', ['--context', K8S_CONTEXT, 'delete', '-k', KUSTOMIZE_LOCAL]);
    done();
  });

  gulp.task('dev:k8s:rollout:restart', (done) => {
    restartApplicationDeployments();
    waitApplicationRollouts();
    done();
  });

  gulp.task('dev:k8s:rollout:image', (done) => {
    setApplicationDeploymentImages();
    waitApplicationRollouts();
    done();
  });

  gulp.task('dev:k8s:release:check-tag', (done) => {
    assertUniqueReleaseImageTag();
    done();
  });

  gulp.task('dev:k8s:release:prompt-tag', async () => {
    await promptReleaseImageTag();
  });

  gulp.task('dev:k8s:up', gulp.series('dev:k8s:images', 'dev:k8s:apply', 'dev:k8s:status'));

  gulp.task(
    'dev:k8s:release',
    gulp.series(
      'dev:k8s:release:prompt-tag',
      'dev:k8s:release:check-tag',
      'dev:k8s:images',
      'dev:k8s:rollout:image',
      'dev:k8s:status',
    ),
  );

  // --- 設計ドキュメント生成（JIG / jig-erd） ---

  /**
   * JIG でコードから設計ドキュメントを生成する。
   *
   * これは「テスト」ではない。生成物と docs/design を突き合わせて、
   * 設計と実装の乖離を人間が確認するための材料である。
   */
  gulp.task('dev:jig', (done) => {
    gradle(['jigReports']);
    syncJigToPortal();
    console.log('\nJIG ドキュメント:');
    JIG_SERVICES.forEach((service) => {
      console.log(`  apps/www/jig/${service}/index.html`);
    });
    console.log('  apps/www/jig/index.html');
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
    DB_SERVICES.forEach((service) => {
      gradle([`:${service}:jigErd`], testcontainersDockerEnv());
    });
    syncJigErdToPortal();
    console.log('\nER 図:');
    DB_SERVICES.forEach((service) => {
      console.log(`  apps/www/jig-erd/${service}/`);
    });
    console.log('  apps/www/jig-erd/index.html');
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
    dev:k8s:images              jar ビルド → イメージ作成 → kind へロード（--tag / DEV_K8S_IMAGE_TAG 対応）
    dev:k8s:diff                Kustomize の合成結果を表示（適用しない）
    dev:k8s:apply               overlays/local を適用
    dev:k8s:up                  images → apply → status を一括実行
    dev:k8s:release             リリース番号入力 → タグ重複確認 → images → rollout image → status を一括実行
    dev:k8s:release:prompt-tag  リリース番号（Docker image tag）をプロンプト入力
    dev:k8s:release:check-tag   指定タグが既存イメージと重複していないことを確認
    dev:k8s:rollout:image       Deployment のイメージを指定タグへ切り替え
    dev:k8s:rollout:restart     アプリ Deployment を再起動して新しい同一タグイメージを反映
    dev:k8s:status              Pod / Service / Ingress の状態
    dev:k8s:logs                全サービスの直近ログ
    dev:k8s:delete              デプロイを削除（クラスタは残す）
    dev:k8s:docs:open           ドキュメントポータル（apps/www）をブラウザで開く

  タグ指定例
    npx gulp dev:k8s:release
    npx gulp dev:k8s:release --tag 20260820-001
    DEV_K8S_IMAGE_TAG=20260820-001 npx gulp dev:k8s:release

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
