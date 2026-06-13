'use strict';

import path from 'path';
import { execSync, spawn } from 'child_process';
import { cleanDockerEnv, isDockerAvailable } from './shared.js';

// ============================================
// 設定
// ============================================

/** Kubernetes namespace */
const NAMESPACE = 'cargo-tracker';

/** アプリイメージ名 */
const IMAGE = 'cargo-tracker/app:latest';

/** アプリの Dockerfile ディレクトリ */
const APP_DIR = path.join(process.cwd(), 'apps/cargo-tracker');

/** Kustomize overlay（local = Docker Desktop / NodePort 30900） */
const OVERLAY_LOCAL = 'ops/k8s/overlays/local';

/** NodePort（overlays/local） */
const NODE_PORT = 30900;

// ============================================
// ヘルパー関数
// ============================================

/**
 * コマンドを実行する（標準出力は引き継ぎ）
 * @param {string} command - 実行するコマンド
 * @param {object} [options] - execSync オプション
 */
function run(command, options = {}) {
  execSync(command, { stdio: 'inherit', env: cleanDockerEnv(), ...options });
}

/**
 * Docker が利用可能か確認し、不可なら警告メッセージを表示して false を返す
 * @returns {boolean} Docker が利用可能なら true
 */
function requireDocker() {
  if (isDockerAvailable()) {
    return true;
  }
  console.warn('Warning: Docker is not running. Skipping this task.');
  console.warn('Please start Docker Desktop and try again.');
  return false;
}

/**
 * /health が応答するまで待機する
 * @param {string} url - ヘルスチェック URL
 * @param {number} [timeoutMs=300000] - タイムアウト（ミリ秒）
 * @returns {Promise<boolean>} 応答したら true
 */
async function waitForHealth(url, timeoutMs = 300000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const res = await fetch(url, { signal: AbortSignal.timeout(5000) });
      if (res.ok) return true;
    } catch {
      // 起動待ち（接続不可・タイムアウトは無視してリトライ）
    }
    await new Promise((resolve) => setTimeout(resolve, 5000));
  }
  return false;
}

// ============================================
// Gulp タスク
// ============================================

/**
 * Kubernetes 運用タスクを gulp に登録する
 * @param {import('gulp').Gulp} gulp - Gulp インスタンス
 */
export default function (gulp) {
  // --- イメージ ---

  gulp.task('k8s:images:build', (done) => {
    if (!requireDocker()) { done(); return; }
    try {
      console.log(`Building ${IMAGE} ...`);
      run(`docker build -t ${IMAGE} .`, { cwd: APP_DIR });
      console.log('Docker Desktop は同一デーモンのため、イメージのロード作業は不要です。');
      done();
    } catch (error) {
      done(error);
    }
  });

  // --- Kustomize ---

  gulp.task('k8s:kustomize:render:local', (done) => {
    try {
      run(`kubectl kustomize ${OVERLAY_LOCAL}`);
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('k8s:kustomize:up:local', (done) => {
    try {
      run(`kubectl apply -k ${OVERLAY_LOCAL}`);
      console.log(`\n状態確認: npx gulp k8s:status / 疎通待機: npx gulp k8s:smoke`);
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('k8s:kustomize:down:local', (done) => {
    try {
      run(`kubectl delete -k ${OVERLAY_LOCAL}`);
      done();
    } catch (error) {
      done(error);
    }
  });

  // --- 運用 ---

  gulp.task('k8s:status', (done) => {
    try {
      run(`kubectl -n ${NAMESPACE} get pods,svc,statefulset,ingress,pvc`);
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('k8s:smoke', async () => {
    run(`kubectl -n ${NAMESPACE} wait deployment/cargo-tracker --for=condition=Available --timeout=300s`);
    // NodePort は Docker Desktop の構成によりホストへ公開されない場合があるため、
    // 環境非依存なポートフォワード経由で疎通確認する
    const localPort = 19000;
    const url = `http://localhost:${localPort}/health`;
    const pf = spawn('kubectl', ['-n', NAMESPACE, 'port-forward', 'svc/cargo-tracker', `${localPort}:9000`], {
      stdio: 'ignore',
      env: cleanDockerEnv()
    });
    try {
      console.log(`Waiting for ${url} (port-forward) ...`);
      if (!(await waitForHealth(url, 60000))) {
        throw new Error('/health が応答しません。npx gulp k8s:status と kubectl logs を確認してください');
      }
      const body = execSync(`curl -s ${url}`, { env: cleanDockerEnv() }).toString();
      console.log(`Smoke test OK: ${body}`);
    } finally {
      pf.kill();
    }
  });

  gulp.task('k8s:port-forward', (done) => {
    try {
      console.log('Ctrl-C で終了します。http://localhost:9000');
      run(`kubectl -n ${NAMESPACE} port-forward svc/cargo-tracker 9000:9000`);
      done();
    } catch (error) {
      done(error);
    }
  });

  gulp.task('k8s:clean', (done) => {
    try {
      run(`kubectl delete -k ${OVERLAY_LOCAL} --ignore-not-found`);
      run(`kubectl -n ${NAMESPACE} delete pvc --all --ignore-not-found`);
      run(`kubectl delete namespace ${NAMESPACE} --ignore-not-found`);
      done();
    } catch (error) {
      done(error);
    }
  });

  // --- ヘルプ ---

  gulp.task('k8s:help', (done) => {
    console.log(`
=== Kubernetes 運用コマンド（Docker Desktop / Kustomize） ===

  k8s:images:build           アプリイメージをビルド（${IMAGE}）
  k8s:kustomize:render:local Kustomize レンダリング（クラスタ不要）
  k8s:kustomize:up:local     デプロイ（kubectl apply -k ${OVERLAY_LOCAL}）
  k8s:kustomize:down:local   削除（PVC は保持）
  k8s:status                 リソースの状態確認
  k8s:smoke                  起動待機 + /health 疎通確認（NodePort ${NODE_PORT}）
  k8s:port-forward           svc/cargo-tracker を localhost:9000 に転送
  k8s:clean                  完全削除（PVC・namespace 含む。DB データが失われる）
  k8s:help                   このヘルプを表示

詳細手順: docs/operation/dev_k8s_instruction.md
`);
    done();
  });
}
