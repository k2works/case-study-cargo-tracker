'use strict';

/**
 * アプリケーション開発タスク（dev:*）
 *
 * 手順は docs/operation/アプリケーション開発環境セットアップ手順書.md に対応する。
 * 環境への操作はここに定義したタスクを使い、使い捨てスクリプトを別途書かない。
 */

import { spawnSync } from 'child_process';
import { cleanDockerEnv } from './shared.js';

const BACKEND_DIR = 'apps/backend';
const FRONTEND_DIR = 'apps/frontend';
const KUSTOMIZE_LOCAL = 'apps/k8s/kustomize/overlays/local';
const KIND_CLUSTER = 'cargo';
const K8S_NAMESPACE = 'cargo';

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

function run(command, args, cwd = '.') {
  const result = spawnSync(command, args, {
    cwd,
    stdio: 'inherit',
    shell: process.platform === 'win32',
    env: cleanDockerEnv(),
  });
  if (result.status !== 0) {
    throw new Error(`${command} ${args.join(' ')} が終了コード ${result.status} で失敗しました`);
  }
}

const gradle = (args) => run('./gradlew', args, BACKEND_DIR);
const npmRun = (args) => run('npm', args, FRONTEND_DIR);

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
    npmRun(['run', 'dev']);
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
    run('kubectl', [
      '-n',
      'ingress-nginx',
      'wait',
      '--for=condition=ready',
      'pod',
      '--selector=app.kubernetes.io/component=controller',
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
    run('docker', ['build', '-t', `cargo-frontend:${IMAGE_TAG}`, '.'], FRONTEND_DIR);
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

  gulp.task('dev:k8s:logs', (done) => {
    run('kubectl', ['-n', K8S_NAMESPACE, 'logs', '-l', 'app', '--all-containers', '--tail=100']);
    done();
  });

  gulp.task('dev:k8s:delete', (done) => {
    run('kubectl', ['delete', '-k', KUSTOMIZE_LOCAL]);
    done();
  });

  gulp.task('dev:k8s:up', gulp.series('dev:k8s:images', 'dev:k8s:apply', 'dev:k8s:status'));

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
`);
    done();
  });
}
