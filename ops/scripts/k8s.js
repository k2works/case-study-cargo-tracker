'use strict';

import path from 'path';
import readline from 'readline';
import { execSync, spawnSync } from 'child_process';
import { cleanDockerEnv, isDockerAvailable } from './shared.js';

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

/** ローカルクラスタ種別（minikube | kind | docker-desktop、K8S_CLUSTER_TYPE で上書き可能） */
const CLUSTER_TYPE = process.env.K8S_CLUSTER_TYPE || 'minikube';

/** kind クラスタ名 */
const KIND_CLUSTER = process.env.K8S_KIND_CLUSTER || 'cargo-tracker';

/** Helm リリース名 */
const HELM_RELEASE = process.env.K8S_HELM_RELEASE || 'cargo-tracker';

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
   * local overlay をデプロイ（kubectl apply -k）
   */
  gulp.task('k8s:kustomize:up:local', (done) => {
    requireCommand('kubectl', 'kubectl を導入してください。');
    kubectl(`apply -k ${path.join(K8S_DIR, 'overlays', 'local')}`);
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
   * Helm でデプロイ（helm upgrade --install、namespace 自動作成）
   */
  gulp.task('k8s:helm:up', (done) => {
    requireCommand('helm', 'helm を導入してください。');
    helm(`upgrade --install ${HELM_RELEASE} ${HELM_CHART} -n ${NAMESPACE} --create-namespace`);
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
  k8s:clean                 namespace を PVC ごと完全削除（y/n 確認あり）
  k8s:help                  このヘルプを表示

【環境変数（.env、K8S_ プレフィックス）】
  K8S_NAMESPACE             namespace（既定: cargo-tracker）
  K8S_CLUSTER_TYPE          minikube | kind | docker-desktop（既定: minikube）
  K8S_KIND_CLUSTER          kind クラスタ名（既定: cargo-tracker）
  K8S_IMAGE_PREFIX          イメージ接頭辞（既定: cargo-tracker）
  K8S_IMAGE_TAG             イメージタグ（既定: latest）
  K8S_HELM_RELEASE          Helm リリース名（既定: cargo-tracker）
    `);
    done();
  });
}
