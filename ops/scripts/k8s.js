'use strict';

import path from 'path';
import readline from 'readline';
import { execSync, spawnSync } from 'child_process';
import { cleanDockerEnv, isDockerAvailable, openUrl } from './shared.js';

// ============================================
// 設定
// ============================================

const ROOT = path.resolve(process.cwd());
const BACKEND_DIR = path.join(ROOT, 'apps', 'backend');
const FRONTEND_DIR = path.join(ROOT, 'apps', 'frontend');
const K8S_DIR = path.join(ROOT, 'ops', 'k8s');
const HELM_CHART = path.join(ROOT, 'ops', 'helm', 'cargo-tracker');

/** Kubernetes namespace（K8S_NAMESPACE で上書き可能） */
const NAMESPACE = process.env.K8S_NAMESPACE || 'cargo-tracker';

/** イメージ接頭辞・タグ（kustomization.yaml / values.yaml の既定と一致） */
const IMAGE_PREFIX = process.env.K8S_IMAGE_PREFIX || 'cargo-tracker';
const IMAGE_TAG = process.env.K8S_IMAGE_TAG || 'latest';

/** ローカルクラスタ種別（docker-desktop | minikube | kind、K8S_CLUSTER_TYPE で上書き可能） */
const CLUSTER_TYPE = process.env.K8S_CLUSTER_TYPE || 'docker-desktop';

/** kind クラスタ名 */
const KIND_CLUSTER = process.env.K8S_KIND_CLUSTER || 'cargo-tracker';

/** Helm リリース名 */
const HELM_RELEASE = process.env.K8S_HELM_RELEASE || 'cargo-tracker';

/** Ingress ホスト名（ブラウザアクセス用、K8S_INGRESS_HOST で上書き可能） */
const INGRESS_HOST = process.env.K8S_INGRESS_HOST || 'cargo-tracker.local';

/**
 * Ingress 公開ホストポート（K8S_INGRESS_HOST_PORT で上書き可能、既定 8080）。
 * port 80 はユーザー権限では bind できず sudo が必要なため、デフォルトは 8080。
 * port 80 を使う場合は `sudo K8S_INGRESS_HOST_PORT=80 npx gulp k8s:expose:local` 等で実行。
 */
const INGRESS_HOST_PORT = process.env.K8S_INGRESS_HOST_PORT || '8080';

/**
 * ingress-nginx コントローラのインストール用マニフェスト（K8S_INGRESS_NGINX_MANIFEST で上書き可能）。
 * docker-desktop / kind には同梱されないため、これを apply して導入する。
 * minikube は `minikube addons enable ingress` を使う（ensureIngressController 参照）。
 */
const INGRESS_NGINX_MANIFEST = process.env.K8S_INGRESS_NGINX_MANIFEST ||
  'https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.11.3/deploy/static/provider/cloud/deploy.yaml';

/**
 * イメージビルド対象（7 ms + frontend）。
 * 既定では apps/backend を context に <name>/Dockerfile をビルドする。
 * frontend は dir/dockerfile を指定して apps/frontend をビルドする。
 */
const SERVICES = [
  { name: 'authms',     port: 8081, label: '認証サービス' },
  { name: 'bookingms',  port: 8082, label: '予約サービス' },
  { name: 'routingms',  port: 8083, label: '経路設計サービス' },
  { name: 'trackingms', port: 8084, label: '追跡サービス' },
  { name: 'handlingms', port: 8085, label: '荷役サービス' },
  { name: 'billingms',  port: 8086, label: '精算サービス' },
  { name: 'gatewayms',  port: 8080, label: 'API Gateway' },
  { name: 'frontendms', port: 80,   label: 'フロントエンド', dir: FRONTEND_DIR, dockerfile: 'Dockerfile' },
];

// ============================================
// ヘルパー関数
// ============================================

/**
 * コマンドが PATH 上に存在するか確認する。
 * @param {string} cmd - 確認するコマンド名（例: 'kubectl'）
 * @returns {boolean} 利用可能なら true
 */
function isCommandAvailable(cmd) {
  const probe = process.platform === 'win32' ? 'where' : 'which';
  const result = spawnSync(probe, [cmd], { stdio: 'ignore' });
  return result.status === 0;
}

/**
 * 必須コマンドの存在を検証し、無ければエラーメッセージを表示して終了する。
 * @param {string} cmd - コマンド名
 * @param {string} hint - 導入方法のヒント
 */
function requireCommand(cmd, hint) {
  if (!isCommandAvailable(cmd)) {
    console.error(`${cmd} が見つかりません。${hint}`);
    process.exit(1);
  }
}

/**
 * kubectl コマンドを実行する。
 * @param {string} args - kubectl に渡す引数文字列
 * @param {object} [opts] - オプション
 * @param {boolean} [opts.ignoreError] - 失敗してもプロセスを継続するか
 */
function kubectl(args, opts = {}) {
  const cmd = `kubectl ${args}`;
  console.log(`[kubectl] ${cmd}`);
  try {
    execSync(cmd, { stdio: 'inherit' });
  } catch (err) {
    if (opts.ignoreError) return;
    console.error(`エラー: ${err.message}`);
    process.exit(1);
  }
}

/**
 * helm コマンドを実行する。
 * @param {string} args - helm に渡す引数文字列
 */
function helm(args) {
  const cmd = `helm ${args}`;
  console.log(`[helm] ${cmd}`);
  try {
    execSync(cmd, { stdio: 'inherit' });
  } catch (err) {
    console.error(`エラー: ${err.message}`);
    process.exit(1);
  }
}

/**
 * 破壊的操作の前に y/n 確認を取る。
 * 非対話環境（CI 等）では自動的に false を返す。
 * @param {string} message - 確認メッセージ
 * @returns {Promise<boolean>} y なら true
 */
function confirmDestructive(message) {
  if (!process.stdin.isTTY) {
    console.error(`${message} 非対話環境では実行を中断します。対話端末から再実行してください。`);
    return Promise.resolve(false);
  }
  return new Promise((resolve) => {
    const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
    rl.question(`${message} [y/N]: `, (answer) => {
      rl.close();
      resolve(answer.trim().toLowerCase() === 'y');
    });
  });
}

/**
 * 1 サービスの Docker イメージをビルドする。
 * @param {{name: string}} svc - サービス定義
 */
function buildImage(svc) {
  const image = `${IMAGE_PREFIX}/${svc.name}:${IMAGE_TAG}`;
  // frontend は apps/frontend を context にビルド。backend ms は apps/backend が context。
  const cwd = svc.dir || BACKEND_DIR;
  const dockerfile = svc.dockerfile || `${svc.name}/Dockerfile`;
  const cmd = `docker build -f ${dockerfile} -t ${image} .`;
  console.log(`[docker build] ${image}`);
  execSync(cmd, { cwd, stdio: 'inherit', env: cleanDockerEnv() });
}

/**
 * ビルド済みイメージをローカルクラスタ（minikube / kind / docker-desktop）へロードする。
 * docker-desktop の場合、docker engine と Kubernetes が統合されているため
 * docker build した時点でイメージがクラスタから参照可能 → load skip。
 * @param {{name: string}} svc - サービス定義
 */
function loadImage(svc) {
  const image = `${IMAGE_PREFIX}/${svc.name}:${IMAGE_TAG}`;
  if (CLUSTER_TYPE === 'docker-desktop') {
    console.log(`[image load:docker-desktop] ${image} (skip: docker engine と統合済み)`);
    return;
  }
  let cmd;
  if (CLUSTER_TYPE === 'kind') {
    cmd = `kind load docker-image ${image} --name ${KIND_CLUSTER}`;
  } else {
    cmd = `minikube image load ${image}`;
  }
  console.log(`[image load:${CLUSTER_TYPE}] ${image}`);
  execSync(cmd, { stdio: 'inherit', env: cleanDockerEnv() });
}

/**
 * ingress-nginx コントローラが既にクラスタへ導入済みか判定する。
 * @returns {boolean} 導入済みなら true
 */
function isIngressControllerInstalled() {
  const result = spawnSync(
    'kubectl',
    ['get', 'deployment', 'ingress-nginx-controller', '-n', 'ingress-nginx'],
    { stdio: 'ignore' },
  );
  return result.status === 0;
}

/**
 * ingress-nginx コントローラを導入し、Ready になるまで待機する（冪等）。
 * minikube は addon、docker-desktop / kind はマニフェスト apply を使う。
 * 既に導入済みなら何もしない。
 */
function ensureIngressController() {
  if (isIngressControllerInstalled()) {
    console.log('[ingress] ingress-nginx コントローラは導入済み（skip）');
    return;
  }
  console.log('[ingress] ingress-nginx コントローラが見つからないため導入します...');
  if (CLUSTER_TYPE === 'minikube') {
    requireCommand('minikube', 'minikube を導入してください。');
    execSync('minikube addons enable ingress', { stdio: 'inherit', env: cleanDockerEnv() });
  } else {
    kubectl(`apply -f ${INGRESS_NGINX_MANIFEST}`);
  }
  console.log('[ingress] コントローラの起動を待機しています...');
  kubectl(
    'wait --namespace ingress-nginx --for=condition=ready pod ' +
    '--selector=app.kubernetes.io/component=controller --timeout=180s',
  );
}

/**
 * デプロイ後の接続手順を分かりやすく表示する。
 */
function printConnectGuidance() {
  const url = INGRESS_HOST_PORT === '80'
    ? `http://${INGRESS_HOST}/`
    : `http://${INGRESS_HOST}:${INGRESS_HOST_PORT}/`;
  console.log(`
──────────────────────────────────────────────
 デプロイ完了。ブラウザでつなぐ手順:
   1) Pod 起動を待つ:        npx gulp k8s:smoke
   2) hosts に登録（要管理者）: 127.0.0.1 ${INGRESS_HOST}
   3) 別ターミナルで公開:     npx gulp k8s:expose:local   （起動したまま）
   4) ブラウザで開く:         npx gulp k8s:open:local
   → ${url}
 （Ingress を使わず直接見る場合: kubectl -n ${NAMESPACE} port-forward svc/frontendms 8888:80 → http://localhost:8888）
──────────────────────────────────────────────
`);
}

// ============================================
// Gulp タスク
// ============================================

export default function (gulp) {
  // ──────────────────────────────────────────
  // イメージ準備（Kustomize / Helm 共通の前提）
  // ──────────────────────────────────────────

  /**
   * 全 7 ms + frontend の Docker イメージをビルド（cargo-tracker/<name>:<tag>）
   */
  gulp.task('k8s:images:build', (done) => {
    if (!isDockerAvailable()) {
      console.error('Docker が起動していません。Docker Desktop を起動してください。');
      process.exit(1);
    }
    SERVICES.forEach(buildImage);
    done();
  });

  /**
   * ビルド済みイメージをローカルクラスタへロード
   * 種別は K8S_CLUSTER_TYPE（minikube | kind | docker-desktop）で切り替える。
   * docker-desktop の場合は docker engine と Kubernetes が統合されているため
   * 追加ツール不要・load 不要（タスクは no-op で完了）。
   */
  gulp.task('k8s:images:load', (done) => {
    if (CLUSTER_TYPE === 'docker-desktop') {
      console.log('K8S_CLUSTER_TYPE=docker-desktop: image load を skip（docker engine と Kubernetes が統合済み）');
      SERVICES.forEach(loadImage);
      done();
      return;
    }
    const tool = CLUSTER_TYPE === 'kind' ? 'kind' : 'minikube';
    requireCommand(tool, `${tool} を導入してください（K8S_CLUSTER_TYPE で切替: minikube | kind | docker-desktop）。`);
    SERVICES.forEach(loadImage);
    done();
  });

  /**
   * イメージのビルド → ロードを連続実行
   */
  gulp.task('k8s:images', gulp.series('k8s:images:build', 'k8s:images:load'));

  // ──────────────────────────────────────────
  // Kustomize 版
  // ──────────────────────────────────────────

  /**
   * local overlay をレンダリング（クラスタ不要、apply 前確認）
   */
  gulp.task('k8s:kustomize:render:local', (done) => {
    requireCommand('kubectl', 'kubectl を導入してください。');
    kubectl(`kustomize ${path.join(K8S_DIR, 'overlays', 'local')}`);
    done();
  });

  /**
   * prod overlay をレンダリング（クラスタ不要、apply 前確認）
   */
  gulp.task('k8s:kustomize:render:prod', (done) => {
    requireCommand('kubectl', 'kubectl を導入してください。');
    kubectl(`kustomize ${path.join(K8S_DIR, 'overlays', 'prod')}`);
    done();
  });

  /**
   * local overlay をデプロイ（kubectl apply -k）。
   * 接続に必要な ingress-nginx コントローラを自動導入し、最後に接続手順を表示する。
   */
  gulp.task('k8s:kustomize:up:local', (done) => {
    requireCommand('kubectl', 'kubectl を導入してください。');
    ensureIngressController();
    kubectl(`apply -k ${path.join(K8S_DIR, 'overlays', 'local')}`);
    printConnectGuidance();
    done();
  });

  /**
   * prod overlay をデプロイ（kubectl apply -k）
   */
  gulp.task('k8s:kustomize:up:prod', (done) => {
    requireCommand('kubectl', 'kubectl を導入してください。');
    kubectl(`apply -k ${path.join(K8S_DIR, 'overlays', 'prod')}`);
    done();
  });

  /**
   * local overlay を削除（kubectl delete -k、PVC は保持）
   */
  gulp.task('k8s:kustomize:down:local', (done) => {
    requireCommand('kubectl', 'kubectl を導入してください。');
    kubectl(`delete -k ${path.join(K8S_DIR, 'overlays', 'local')}`, { ignoreError: true });
    done();
  });

  /**
   * prod overlay を削除（kubectl delete -k、PVC は保持）
   */
  gulp.task('k8s:kustomize:down:prod', (done) => {
    requireCommand('kubectl', 'kubectl を導入してください。');
    kubectl(`delete -k ${path.join(K8S_DIR, 'overlays', 'prod')}`, { ignoreError: true });
    done();
  });

  // ──────────────────────────────────────────
  // Helm 版
  // ──────────────────────────────────────────

  /**
   * Helm チャートの構文チェック（helm lint）
   */
  gulp.task('k8s:helm:lint', (done) => {
    requireCommand('helm', 'helm を導入してください（https://helm.sh/docs/intro/install/）。');
    helm(`lint ${HELM_CHART}`);
    done();
  });

  /**
   * Helm チャートをレンダリング（helm template、クラスタ不要）
   */
  gulp.task('k8s:helm:render', (done) => {
    requireCommand('helm', 'helm を導入してください。');
    helm(`template ${HELM_RELEASE} ${HELM_CHART} -n ${NAMESPACE}`);
    done();
  });

  /**
   * Helm でデプロイ（helm upgrade --install、namespace 自動作成）。
   * 接続に必要な ingress-nginx コントローラを自動導入し、最後に接続手順を表示する。
   * Helm チャートは Ingress リソースのみ生成し、コントローラは導入しないため
   * ここで ensureIngressController を呼ぶ（k8s:clean 後でもそのまま繋がるように）。
   */
  gulp.task('k8s:helm:up', (done) => {
    requireCommand('helm', 'helm を導入してください。');
    requireCommand('kubectl', 'kubectl を導入してください。');
    ensureIngressController();
    helm(`upgrade --install ${HELM_RELEASE} ${HELM_CHART} -n ${NAMESPACE} --create-namespace`);
    printConnectGuidance();
    done();
  });

  /**
   * 直前のリビジョンにロールバック（helm rollback）
   */
  gulp.task('k8s:helm:rollback', (done) => {
    requireCommand('helm', 'helm を導入してください。');
    helm(`rollback ${HELM_RELEASE} -n ${NAMESPACE}`);
    done();
  });

  /**
   * Helm リリースを削除（helm uninstall）
   */
  gulp.task('k8s:helm:down', (done) => {
    requireCommand('helm', 'helm を導入してください。');
    helm(`uninstall ${HELM_RELEASE} -n ${NAMESPACE}`);
    done();
  });

  // ──────────────────────────────────────────
  // 共通運用（方式非依存）
  // ──────────────────────────────────────────

  /**
   * namespace 内のリソース状態を表示
   */
  gulp.task('k8s:status', (done) => {
    requireCommand('kubectl', 'kubectl を導入してください。');
    kubectl(`-n ${NAMESPACE} get pods,svc,statefulset,ingress`);
    done();
  });

  /**
   * 全 Deployment が Available になるまで待機（疎通 smoke）
   */
  gulp.task('k8s:smoke', (done) => {
    requireCommand('kubectl', 'kubectl を導入してください。');
    kubectl(`-n ${NAMESPACE} wait --for=condition=available --timeout=300s deployment --all`);
    done();
  });

  /**
   * gatewayms をローカルへポートフォワード（8080→8080、Ctrl+C で終了）
   */
  gulp.task('k8s:port-forward', (done) => {
    requireCommand('kubectl', 'kubectl を導入してください。');
    kubectl(`-n ${NAMESPACE} port-forward svc/gatewayms 8080:8080`);
    done();
  });

  /**
   * ingress-nginx コントローラを導入（冪等）。Ready まで待機する。
   * docker-desktop / kind は同梱されないため必要。minikube は addon を使う。
   * up タスクからも自動で呼ばれるが、単体導入したい場合に使う。
   */
  gulp.task('k8s:ingress:install', (done) => {
    requireCommand('kubectl', 'kubectl を導入してください。');
    ensureIngressController();
    console.log('[ingress] 導入完了。`npx gulp k8s:expose:local` で公開できます。');
    done();
  });

  /**
   * Ingress（ingress-nginx）を 127.0.0.1:${INGRESS_HOST_PORT} へ公開
   * （Ctrl+C で終了、起動したまま使う）。
   * docker-desktop はノードが docker ネットワーク内のため LoadBalancer IP に
   * ホストから直接届かない。本タスクで ${INGRESS_HOST}:${INGRESS_HOST_PORT} を
   * 127.0.0.1 で利用可能にする。コントローラ未導入なら自動導入する。
   *
   * port は既定 8080（ユーザー権限で bind 可能）。port 80 を使う場合は
   * `sudo K8S_INGRESS_HOST_PORT=80 npx gulp k8s:expose:local` のように
   * sudo + 環境変数で実行する。
   *
   * 前提: hosts に "127.0.0.1 ${INGRESS_HOST}" を登録済みであること。
   */
  gulp.task('k8s:expose:local', (done) => {
    requireCommand('kubectl', 'kubectl を導入してください。');
    ensureIngressController();
    const url = INGRESS_HOST_PORT === '80'
      ? `http://${INGRESS_HOST}/`
      : `http://${INGRESS_HOST}:${INGRESS_HOST_PORT}/`;
    console.log(`Ingress を ${url} で公開します（このプロセスは起動したままにしてください）。`);
    kubectl(`-n ingress-nginx port-forward svc/ingress-nginx-controller ${INGRESS_HOST_PORT}:80`);
    done();
  });

  /**
   * ローカルデプロイのフロントエンド（公開エントリポイント）をブラウザで開く。
   * 事前に k8s:expose:local（Ingress を ${INGRESS_HOST_PORT} 番へ公開）を別ターミナルで起動し、
   * hosts に "127.0.0.1 ${INGRESS_HOST}" を登録しておくこと。
   */
  gulp.task('k8s:open:local', (done) => {
    const url = INGRESS_HOST_PORT === '80'
      ? `http://${INGRESS_HOST}/`
      : `http://${INGRESS_HOST}:${INGRESS_HOST_PORT}/`;
    console.log(`ブラウザで ${url} を開きます。`);
    console.log('表示されない場合は別ターミナルで `npx gulp k8s:expose:local` を起動し、hosts に "127.0.0.1 ' + INGRESS_HOST + '" があるか確認してください。');
    try {
      openUrl(url);
      done();
    } catch (error) {
      done(error);
    }
  });

  /**
   * namespace ごと完全削除（PVC・永続データを破棄）
   * 実行前に対話的に y/n 確認を取る。
   */
  gulp.task('k8s:clean', async (done) => {
    requireCommand('kubectl', 'kubectl を導入してください。');
    const ok = await confirmDestructive(
      `k8s:clean は namespace ${NAMESPACE} を PVC ごと完全削除します。続行しますか？`,
    );
    if (!ok) {
      console.log('k8s:clean をキャンセルしました。');
      done();
      return;
    }
    kubectl(`delete namespace ${NAMESPACE}`, { ignoreError: true });
    done();
  });

  // ──────────────────────────────────────────
  // ヘルプ
  // ──────────────────────────────────────────

  /**
   * Kubernetes 運用タスク一覧を表示
   */
  gulp.task('k8s:help', (done) => {
    console.log(`
=== Kubernetes 運用タスク ===

詳細は docs/operation/Kubernetes運用手順書.md を参照。
namespace: ${NAMESPACE} / cluster: ${CLUSTER_TYPE} / image: ${IMAGE_PREFIX}/<ms>:${IMAGE_TAG}

【イメージ準備（Kustomize / Helm 共通の前提）】
  k8s:images:build          全 7 ms + frontend の Docker イメージをビルド
  k8s:images:load           イメージをローカルクラスタへロード
                              - minikube       : minikube image load
                              - kind           : kind load docker-image
                              - docker-desktop : skip（docker engine と統合）
  k8s:images                build → load を連続実行

【Kustomize 版】
  k8s:kustomize:render:local  local overlay をレンダリング（クラスタ不要）
  k8s:kustomize:render:prod   prod overlay をレンダリング（クラスタ不要）
  k8s:kustomize:up:local      local overlay をデプロイ（apply -k）
  k8s:kustomize:up:prod       prod overlay をデプロイ（apply -k）
  k8s:kustomize:down:local    local overlay を削除（PVC は保持）
  k8s:kustomize:down:prod     prod overlay を削除（PVC は保持）

【Helm 版】
  k8s:helm:lint             helm lint（構文チェック）
  k8s:helm:render           helm template（レンダリング、クラスタ不要）
  k8s:helm:up               helm upgrade --install（namespace 自動作成）
  k8s:helm:rollback         直前のリビジョンにロールバック
  k8s:helm:down             helm uninstall

【共通運用】
  k8s:status                namespace 内のリソース状態を表示
  k8s:smoke                 全 Deployment が Available になるまで待機
  k8s:port-forward          gatewayms を 8080 にポートフォワード
  k8s:ingress:install       ingress-nginx コントローラを導入（冪等、up タスクから自動実行）
  k8s:expose:local          Ingress を 127.0.0.1:${INGRESS_HOST_PORT} へ公開（起動したまま使う、
                              既定 8080 でユーザー権限 OK、80 を使う場合は sudo + 環境変数）
  k8s:open:local            ブラウザで ${INGRESS_HOST_PORT === '80' ? `http://${INGRESS_HOST}/` : `http://${INGRESS_HOST}:${INGRESS_HOST_PORT}/`} を開く
  k8s:clean                 namespace を PVC ごと完全削除（y/n 確認あり）
  k8s:help                  このヘルプを表示

【環境変数（.env、K8S_ プレフィックス）】
  K8S_NAMESPACE             namespace（既定: cargo-tracker）
  K8S_CLUSTER_TYPE          docker-desktop | minikube | kind（既定: docker-desktop）
  K8S_KIND_CLUSTER          kind クラスタ名（既定: cargo-tracker）
  K8S_IMAGE_PREFIX          イメージ接頭辞（既定: cargo-tracker）
  K8S_IMAGE_TAG             イメージタグ（既定: latest）
  K8S_HELM_RELEASE          Helm リリース名（既定: cargo-tracker）
  K8S_INGRESS_HOST          ブラウザアクセス用ホスト名（既定: cargo-tracker.local）
  K8S_INGRESS_HOST_PORT     Ingress 公開ホストポート（既定: 8080、80 を使う場合は sudo 必要）
    `);
    done();
  });
}
