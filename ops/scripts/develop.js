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
const KUSTOMIZE_LOCAL = 'ops/k8s/kustomize/overlays/local';
const KIND_CLUSTER = 'cargo';
const K8S_NAMESPACE = 'cargo';

/** ローカル統合環境の PostgreSQL 利用者（ops/k8s/kustomize/base/postgres.yaml と揃える）。 */
const K8S_POSTGRES_USER = 'cargo_tracker';
/**
 * kubectl の接続先。
 *
 * 明示しないと「そのとき選ばれているコンテキスト」に対して実行される。docker-desktop の
 * Kubernetes を併用していると、同じ名前空間が両方に存在し、**別のクラスタを操作していても
 * 何も言わずに成功する**。ローカル統合環境が動かない原因として最も気づきにくい。
 */
const K8S_CONTEXT = `kind-${KIND_CLUSTER}`;
// Ingress が localhost の 80 番で公開する（ops/k8s/kustomize/base/ingress.yaml）
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
  'simulationms',
];
const DEFAULT_IMAGE_TAG = '0.0.1';

/** 専用データベースを持つサービス。jig-erd の ER 図はこの単位で生成される。 */
const DB_SERVICES = ['authms', 'bookingms', 'routingms', 'trackingms', 'handlingms', 'billingms',
  'simulationms'];

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
  const candidates = [
    process.env.npm_execpath,
    join(dirname(dirname(process.execPath)), 'lib/node_modules/npm/bin/npm-cli.js'),
    join(dirname(process.execPath), 'node_modules/npm/bin/npm-cli.js'),
  ].filter(Boolean);

  const npmCli = candidates.find((candidate) => existsSync(candidate));
  if (!npmCli) {
    throw new Error(`npm-cli.js が見つかりません: ${candidates.join(', ')}`);
  }
  return npmCli;
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
    console.log(
      `現在の最新リリース番号: ${latestReleaseImageTagLabel()}。` +
        ` リリース番号が未指定のため、既定のタグ ${DEFAULT_IMAGE_TAG} を使います。`,
    );
    return;
  }

  const rl = createInterface({ input: process.stdin, output: process.stdout });
  try {
    console.log(`現在の最新リリース番号: ${latestReleaseImageTagLabel()}`);
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
 * ローカル Docker にある cargo アプリケーションイメージの最新タグを返す。
 *
 * `docker image ls` は既定で作成日時の新しい順に並ぶため、release 時に作られた
 * cargo-* イメージの先頭タグを現在の最新リリース番号として扱う。
 *
 * @returns {string | undefined} 最新の Docker イメージタグ
 */
function latestReleaseImageTag() {
  const result = spawnCommand('docker', ['image', 'ls', '--format', '{{.Repository}}:{{.Tag}}'], {
    stdio: ['ignore', 'pipe', 'ignore'],
    env: cleanDockerEnv(),
  });
  if (result.status !== 0) {
    return undefined;
  }

  const deploymentRepositories = new Set(K8S_DEPLOYMENTS.map((service) => `cargo-${service}`));
  return String(result.stdout)
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .map((image) => {
      const tagSeparatorIndex = image.lastIndexOf(':');
      return {
        repository: image.slice(0, tagSeparatorIndex),
        tag: image.slice(tagSeparatorIndex + 1),
      };
    })
    .find(({ repository, tag }) => deploymentRepositories.has(repository) && tag !== '<none>')?.tag;
}

/**
 * 最新リリース番号の表示値を返す。
 *
 * @returns {string} プロンプト表示用の最新リリース番号
 */
function latestReleaseImageTagLabel() {
  if (!isDockerAvailable()) {
    return '取得できません';
  }
  return latestReleaseImageTag() ?? 'なし';
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
 * Deployment がいま指しているイメージ名を返す。
 *
 * @param {string} service サービス名
 * @returns {string | undefined} イメージ名（取得できない場合は undefined）
 */
function currentDeploymentImage(service) {
  const result = spawnCommand(
    'kubectl',
    [
      '--context', K8S_CONTEXT,
      '-n', K8S_NAMESPACE,
      'get', k8sDeployment(service),
      '-o', `jsonpath={.spec.template.spec.containers[?(@.name=="${service}")].image}`,
    ],
    { stdio: ['ignore', 'pipe', 'pipe'], env: cleanDockerEnv() },
  );
  if (result.status !== 0) {
    return undefined;
  }
  return String(result.stdout ?? '').trim() || undefined;
}

/**
 * アプリケーション Deployment に指定タグのイメージを設定する。
 *
 * **タグが今と同じでも Pod を作り直す。** `kubectl set image` は spec が変わったときだけ
 * ロールアウトを起こす。既定のタグ（0.0.1）は base のマニフェストに直書きされているため、
 * `dev:k8s:images` でイメージを作り直しても spec は変わらず、走っている Pod は古い jar を
 * 掴んだままになる。IT10 はこれで「反映したつもり」の実環境確認をして誤った赤を見た。
 * タグが同じときは `rollout restart` に切り替えて、必ず新しいイメージを掴み直させる。
 *
 * @param {string} tag Docker イメージタグ
 */
function setApplicationDeploymentImages(tag = imageTag()) {
  K8S_DEPLOYMENTS.forEach((service) => {
    const desired = dockerImage(service, tag);
    const current = currentDeploymentImage(service);

    if (current === desired) {
      console.log(`${service}: イメージ名が今と同じ（${desired}）ため、再起動で掴み直します。`);
      run('kubectl', ['--context', K8S_CONTEXT, '-n', K8S_NAMESPACE, 'rollout', 'restart', k8sDeployment(service)]);
      return;
    }

    console.log(`${service}: ${current ?? '(不明)'} → ${desired}`);
    run('kubectl', [
      '--context', K8S_CONTEXT,
      '-n',
      K8S_NAMESPACE,
      'set',
      'image',
      k8sDeployment(service),
      `${service}=${desired}`,
    ]);
  });
}

/**
 * アプリケーション Pod の起動時刻とイメージを表示する。
 *
 * タスクの成功メッセージは「反映できたか」を判別しない（IT10 Problem 7）。
 * 起動時刻が今より前のままなら、その Pod は作り直されていない。
 */
function reportApplicationPodStartTimes() {
  console.log('');
  console.log('反映を確かめてください（起動時刻が今でなければ、その Pod は作り直されていません）。');
  run('kubectl', [
    '--context', K8S_CONTEXT,
    '-n', K8S_NAMESPACE,
    'get', 'pods',
    '-l', 'app',
    '-o', 'custom-columns=POD:.metadata.name,IMAGE:.spec.containers[0].image,STARTED:.status.startTime,STATUS:.status.phase',
  ]);
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

/**
 * PostgreSQL Deployment のロールアウトと Ready を待つ。
 */
function waitPostgresReady() {
  run('kubectl', [
    '--context',
    K8S_CONTEXT,
    '-n',
    K8S_NAMESPACE,
    'rollout',
    'status',
    'deployment/postgres',
    '--timeout=180s',
  ]);
  // **ラベルだけで待たない。** `-l app=postgres` は<strong>終了中の古い Pod にも
  // 一致する</strong>——それは二度と Ready にならないため、rollout が成功したあとも
  // ここで必ず 180 秒待って失敗する（実際に db:reset が 2 回続けて落ちた）。
  // いま動いている ReplicaSet の Pod だけを待つ。
  const newest = spawnCommand('kubectl', [
    '--context',
    K8S_CONTEXT,
    '-n',
    K8S_NAMESPACE,
    'get',
    'replicaset',
    '-l',
    'app=postgres',
    '--sort-by=.metadata.creationTimestamp',
    '-o',
    'jsonpath={.items[-1:].metadata.labels.pod-template-hash}',
  ], { stdio: ['ignore', 'pipe', 'ignore'] });
  const hash = newest.status === 0 ? String(newest.stdout).trim() : '';
  run('kubectl', [
    '--context',
    K8S_CONTEXT,
    '-n',
    K8S_NAMESPACE,
    'wait',
    '--for=condition=ready',
    'pod',
    '-l',
    hash === '' ? 'app=postgres' : `app=postgres,pod-template-hash=${hash}`,
    '--timeout=180s',
  ]);
}

/**
 * DB を持つサービスの Deployment を再起動する。
 */
function restartDatabaseServiceDeployments() {
  DB_SERVICES.forEach((service) => {
    run('kubectl', ['--context', K8S_CONTEXT, '-n', K8S_NAMESPACE, 'rollout', 'restart', k8sDeployment(service)]);
  });
}

/**
 * DB を持つサービスの Deployment ロールアウト完了を待つ。
 */
function waitDatabaseServiceRollouts() {
  DB_SERVICES.forEach((service) => {
    run('kubectl', [
      '--context',
      K8S_CONTEXT,
      '-n',
      K8S_NAMESPACE,
      'rollout',
      'status',
      k8sDeployment(service),
      '--timeout=180s',
    ]);
  });
}


/**
 * デッドレターに溜まったイベントを、元の交換機へ送り直す。
 *
 * 「番号は渡されたのに追えない」は、ブローカーの一時障害や購読側の不具合で確実に起きる。
 * 発行側は成功しているので、放っておくと**どこにも異常が残らないまま**追跡だけが欠ける。
 * 手で送り直す手段を用意しておく（Outbox を入れるのはその次の話である）。
 *
 * @param {string} deadLetterQueue 送り直す元のデッドレターキュー
 * @param {string} exchange 送り先の交換機
 * @param {string} routingKey 送り先のルーティングキー
 * @param {number} limit 一度に送り直す最大件数
 */
function redeliverDeadLetters(deadLetterQueue, exchange, routingKey, limit) {
  // rabbitmqadmin は管理プラグインの CLI で、公式イメージに同梱されている。
  // 独自のスクリプトを書かずに済む。
  //
  // 1 件ずつ取り出して送り直す。payload_file は count=1 のときしか使えないため、
  // まとめて取り出すことはできない。
  // 送り直すときも本番と同じヘッダを付ける。付け忘れると購読側は読めず、
  // そのままデッドレターへ戻る——「再実行したのに直らない」ことになる
  const properties =
    `'{"content_type":"application/json",` +
    `"headers":{"__TypeId__":"${TRACKING_NUMBER_ISSUED_TYPE_ID}"}}'`;

  const script = [
    'cd /tmp',
    `i=0; while [ $i -lt ${limit} ]; do`,
    '  rm -f dlq-payload',
    `  rabbitmqadmin get queue=${deadLetterQueue} count=1 ackmode=ack_requeue_false payload_file=dlq-payload > /dev/null 2>&1 || break`,
    '  [ -s dlq-payload ] || break',
    `  rabbitmqadmin publish exchange=${exchange} routing_key=${routingKey} properties=${properties} < dlq-payload > /dev/null || break`,
    '  i=$((i+1))',
    'done',
    'rm -f dlq-payload',
    'echo "送り直した件数: $i"',
  ].join('\n');

  run('kubectl', [
    '--context', K8S_CONTEXT, '-n', K8S_NAMESPACE,
    'exec', 'deploy/rabbitmq', '--', 'sh', '-c', script,
  ]);
}

/**
 * プロデューサが `__TypeId__` に載せる型名。
 *
 * 送り直すときも本番と同じヘッダを付ける。付け忘れると、購読側は読めずに
 * そのままデッドレターへ戻る——「再実行したのに直らない」ことになる。
 */
const TRACKING_NUMBER_ISSUED_TYPE_ID =
  'com.example.bookingms.application.port.TrackingNumberIssued';

/**
 * 取りこぼしを数える。
 *
 * 追跡番号を発行済みなのに追跡が始まっていない予約は、荷主から見ると
 * 「番号はもらったのに追えない」状態である。件数が出るだけでは次の行動に繋がらないため、
 * 予約番号まで出す。
 */
function reportMissingTracking() {
  const issued = psql('booking_db', `SELECT tracking_number FROM cargo
      WHERE tracking_number IS NOT NULL ORDER BY tracking_number`);
  const tracked = psql('tracking_db', `SELECT tracking_number FROM tracking_activity
      ORDER BY tracking_number`);
  const trackedSet = new Set(tracked);
  const missing = issued.filter((number) => !trackedSet.has(number));

  console.log(`発行済み: ${issued.length} 件 / 追跡あり: ${tracked.length} 件`);
  if (missing.length === 0) {
    console.log('取りこぼしはありません。');
    return;
  }
  console.log(`\n**取りこぼし ${missing.length} 件**（番号は渡したのに追跡が始まっていない）:`);
  missing.forEach((number) => console.log(`  ${number}`));
  console.log('\n送り直すには: npx gulp dev:k8s:events:redeliver');
}

/**
 * ローカル統合環境の DB に読み取りの問い合わせを投げ、1 列目を配列で返す。
 *
 * @param {string} database 対象データベース
 * @param {string} sql 読み取りの SQL
 * @returns {string[]} 1 列目の値
 */
function psql(database, sql) {
  const result = spawnCommand('kubectl', [
    '--context', K8S_CONTEXT, '-n', K8S_NAMESPACE,
    'exec', 'deploy/postgres', '--',
    'psql', '-U', K8S_POSTGRES_USER, '-d', database, '-t', '-A', '-c', sql,
  ], { stdio: ['ignore', 'pipe', 'inherit'], env: cleanDockerEnv() });
  if (result.status !== 0) {
    throw new Error(`${database} への問い合わせが失敗しました`);
  }
  return String(result.stdout).split('\n').map((line) => line.trim()).filter(Boolean);
}



/**
 * 足りないデータベースを作る（既存データは消さない）。
 *
 * <strong>init-databases.sql は、データディレクトリが空のときにしか走らない。</strong>
 * サービスを足して SQL に 1 行加えても、<strong>すでに動いている環境には反映されない</strong>——
 * 新しいサービスだけが「データベースがありません」で起動に失敗する。
 * 症状は新サービス側に出るため、原因が初期化スクリプトだと分かりにくい
 * （RabbitMQ の交換機を宣言し直せない話と同じ形である）。
 *
 * db:reset は既存データを消すため、確かめたいものまで一緒に消える。ここでは
 * <strong>足りないものだけを作る</strong>。
 *
 * @returns {string[]} 作成したデータベース名
 */
function ensureDatabases() {
  const created = [];
  DB_SERVICES.forEach((service) => {
    const database = `${service.replace(/ms$/, '')}_db`;
    const existing = psql('postgres',
      `SELECT 1 FROM pg_database WHERE datname = '${database}'`);
    if (existing.length > 0) {
      return;
    }
    psql('postgres', `CREATE DATABASE ${database}`);
    created.push(database);
  });
  return created;
}

/**
 * 交換機を作り直す。
 *
 * RabbitMQ は<strong>既存の交換機を違う引数で宣言し直せない</strong>（PRECONDITION_FAILED）。
 * alternate-exchange を足したときのように引数を変えると、すでに交換機がある環境では
 * 宣言がそこで失敗し、<strong>その後ろに続くキューの宣言まで行われない</strong>。
 * 症状は「新しいキューが無い」で出るため、原因が交換機だと分かりにくい。
 *
 * Testcontainers のテストはブローカーが毎回新品なので、この形を検出できない。
 *
 * <p>交換機を消してもキューのメッセージは消えない。結びつけは消えるが、各サービスが
 * 起動時に宣言し直す。
 *
 * @param {string[]} exchanges 作り直す交換機
 */
function recreateExchanges(exchanges) {
  exchanges.forEach((exchange) => {
    run('kubectl', [
      '--context', K8S_CONTEXT, '-n', K8S_NAMESPACE,
      'exec', 'deploy/rabbitmq', '--',
      // rabbitmqctl に交換機の削除は無い。管理プラグインの CLI を使う
      'rabbitmqadmin', 'delete', 'exchange', `name=${exchange}`,
    ]);
  });
  console.log('\n交換機を消しました。サービスを再起動すると、新しい引数で宣言し直されます。');
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
    run('kind', ['create', 'cluster', '--config', 'ops/k8s/kind-cluster.yaml']);
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
   * Ingress は localhost の 80 番で公開している（ops/k8s/kustomize/base/ingress.yaml）。
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
    reportApplicationPodStartTimes();
    done();
  });

  gulp.task('dev:k8s:rollout:image', (done) => {
    setApplicationDeploymentImages();
    waitApplicationRollouts();
    reportApplicationPodStartTimes();
    done();
  });

  gulp.task('dev:k8s:db:ensure', (done) => {
    const created = ensureDatabases();
    console.log(created.length === 0
      ? 'すべてのデータベースがすでにあります。'
      : `作成しました: ${created.join(', ')}（該当サービスを再起動してください）`);
    done();
  });

  gulp.task('dev:k8s:db:reset', (done) => {
    console.log('k8s ローカル統合環境の PostgreSQL を初期化します。既存データは削除されます。');
    run('kubectl', ['--context', K8S_CONTEXT, '-n', K8S_NAMESPACE, 'rollout', 'restart', 'deployment/postgres']);
    waitPostgresReady();
    restartDatabaseServiceDeployments();
    waitDatabaseServiceRollouts();
    run('kubectl', ['--context', K8S_CONTEXT, '-n', K8S_NAMESPACE, 'get', 'pods', '-l', 'app']);
    done();
  });

  gulp.task('dev:k8s:release:check-tag', (done) => {
    assertUniqueReleaseImageTag();
    done();
  });

  gulp.task('dev:k8s:release:prompt-tag', async () => {
    await promptReleaseImageTag();
  });



  /**
   * 交換機の引数を変えたときに作り直す。
   *
   * 引数（alternate-exchange など）を変えても、既存の交換機はそのままでは宣言し直せない。
   * このタスクで消してから、アプリを再起動する。
   */
  gulp.task('dev:k8s:events:redeclare', (done) => {
    recreateExchanges(['cargoBookingChannel', 'cargoHandlingChannel']);
    restartApplicationDeployments();
    waitApplicationRollouts();
    done();
  });

  /**
   * 取りこぼし（発行済みだが追跡が無い予約）を照会する。
   *
   * イベントは届かなくても発行側がエラーにならないため、この照会が唯一の気づく手段である。
   */
  gulp.task('dev:k8s:events:missing', (done) => {
    reportMissingTracking();
    done();
  });

  /**
   * デッドレターのイベントを元の交換機へ送り直す。
   *
   * 送り直す前に dev:k8s:events:missing で対象を確かめること。購読側の不具合が
   * 直っていなければ、送り直したイベントはそのままデッドレターへ戻る。
   */
  gulp.task('dev:k8s:events:redeliver', (done) => {
    redeliverDeadLetters(
      'trackingms.tracking-number-issued.dlq',
      'cargoBookingChannel',
      'cargo.tracking-number-issued',
      Number(process.env.DLQ_REDELIVER_LIMIT ?? 50),
    );
    reportMissingTracking();
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

  /**
   * ドキュメントポータル（apps/www）へ配信する成果物を最新化する。
   *
   * cargo-www イメージは apps/www を静的配信するだけなので、Docker build 前に
   * docs / manual / JIG / jig-erd を apps/www 配下へ配置しておく。
   */
  gulp.task('dev:k8s:www:artifacts', gulp.series('mkdocs:build', 'manual:build', 'dev:jig', 'dev:jig-erd'));

  gulp.task(
    'dev:k8s:release',
    gulp.series(
      'dev:k8s:release:prompt-tag',
      'dev:k8s:release:check-tag',
      'dev:k8s:www:artifacts',
      'dev:k8s:images',
      'dev:k8s:apply',
      'dev:k8s:rollout:image',
      'dev:k8s:status',
    ),
  );

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
    dev:k8s:release             リリース番号入力 → タグ重複確認 → docs/manual/JIG/jig-erd → images → apply → rollout image → status を一括実行
    dev:k8s:release:prompt-tag  リリース番号（Docker image tag）をプロンプト入力
    dev:k8s:release:check-tag   指定タグが既存イメージと重複していないことを確認
    dev:k8s:www:artifacts       apps/www 配信用の docs / manual / JIG / jig-erd を生成
    dev:k8s:rollout:image       Deployment のイメージを指定タグへ切り替え
    dev:k8s:rollout:restart     アプリ Deployment を再起動して新しい同一タグイメージを反映
    dev:k8s:events:missing      取りこぼし（発行済みだが追跡が無い予約）を照会する
    dev:k8s:events:redeclare    交換機の引数を変えたときに作り直して再起動する
    dev:k8s:events:redeliver    デッドレターのイベントを元の交換機へ送り直す（DLQ_REDELIVER_LIMIT 対応）
    dev:k8s:db:ensure           足りないデータベースだけを作る（既存データは消さない）
    dev:k8s:db:reset            PostgreSQL を初期化し、DB 利用サービスの Flyway を再実行
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
