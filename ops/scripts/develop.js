'use strict';

/**
 * アプリケーション開発タスク（dev:*）
 *
 * 手順は docs/operation/cargo-tracker/アプリケーション開発環境セットアップ手順書.md に対応する。
 * 手順書に載っている操作はここに定義し、使い捨てスクリプトを別途書かない。
 *
 * 環境（kind クラスタ・ミドルウェア）への操作は ops/scripts/cargo_tracker.js に置く。
 * ここに置くのは「開発者が自分の機械で回すもの」だけである。
 */

import { spawnSync } from 'child_process';
import { cleanDockerEnv, openUrl } from './shared.js';

const BACKEND_DIR = 'apps/cargo-tracker/backend';
const FRONTEND_DIR = 'apps/cargo-tracker/frontend';

/** 既定で起動するバックエンドサービス。 */
const DEFAULT_SERVICE = 'bookingms';

/** 業務サービス（architecture_backend.md）。 */
const SERVICES = [
  'gatewayms',
  'authms',
  'bookingms',
  'routingms',
  'trackingms',
  'handlingms',
  'billingms',
];

/**
 * JIG の対象サブプロジェクト数（業務 8 + テスト専用 2）。
 * jigReports は全サブプロジェクトに登録されるため、テスト専用の 2 つも出力される。
 */
const JIG_MODULE_COUNT = 10;

/** 専用データベースを持つサービス。jig-erd の ER 図はこの単位で生成される。 */
const DB_SERVICES = ['authms', 'bookingms', 'routingms', 'trackingms', 'handlingms', 'billingms'];

/**
 * Windows shell に渡す引数を引用する。
 *
 * npm.cmd / gradlew.bat は Windows では shell 経由で実行する必要がある。そのまま
 * spawnSync に渡すと空白を含む引数が分割されるため、明示的に command line を組み立てる。
 *
 * @param {string} value 引数
 * @returns {string} 引用済み引数
 */
function quoteWindowsArg(value) {
  return `"${String(value).replace(/^"|"$/g, '').replace(/"/g, '\\"')}"`;
}

/**
 * OS 差を吸収して外部コマンドを実行する。失敗したら例外を投げる。
 *
 * @param {string} command コマンド
 * @param {string[]} args 引数
 * @param {string} cwd 作業ディレクトリ
 * @param {object} extraEnv 追加の環境変数
 */
function run(command, args, cwd = '.', extraEnv = {}) {
  const env = { ...cleanDockerEnv(), ...extraEnv };
  const result = process.platform === 'win32'
    ? spawnSync([command, ...args].map(quoteWindowsArg).join(' '), [], { cwd, stdio: 'inherit', env, shell: true })
    : spawnSync(command, args, { cwd, stdio: 'inherit', env });
  if (result.status !== 0) {
    throw new Error(`${command} ${args.join(' ')} が終了コード ${result.status} で失敗しました`);
  }
}

const gradlew = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';
const gradle = (args, extraEnv = {}) => run(gradlew, args, BACKEND_DIR, extraEnv);
const npmRun = (args) => run('npm', args, FRONTEND_DIR);

export default function (gulp) {
  // --- バックエンド ---

  /**
   * 動作確認用の利用者を入れる（ADR-0004）。
   *
   * <p>dev:* タスクは定義からして開発環境である。ここで明示的に渡す一方、
   * アプリケーション側の既定は無効のままにしてある。bootJar をそのまま
   * 別の環境で起動しても、この利用者は入らない。</p>
   *
   * <p>環境変数ではなく起動引数で渡す。bootRun が起こす JVM は Gradle
   * デーモンの環境を継ぐが、デーモンは呼び出しをまたいで生き残るので、
   * 環境変数だと「最初に立てたときの値」が効いてしまう。</p>
   */
  const DEMO_USERS_ARG = '--args=--cargo-tracker.demo-users=true';

  gulp.task('dev:backend', (done) => {
    gradle([`:${DEFAULT_SERVICE}:bootRun`, DEMO_USERS_ARG]);
    done();
  });

  SERVICES.forEach((service) => {
    gulp.task(`dev:backend:${service}`, (done) => {
      gradle([`:${service}:bootRun`, DEMO_USERS_ARG]);
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
    gradle(['checkstyleMain', 'checkstyleTest', 'spotbugsMain', 'spotbugsTest']);
    done();
  });

  // ArchUnit とカバレッジ閾値はフルビルドでしか働かない。
  // Port の追加や ADR の起票を伴う変更では必ずこれを実行する。
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

  gulp.task('dev:frontend:typecheck', (done) => {
    // tsc --noEmit ではない。プロジェクト参照構成（files: [] + references）では
    // 何も検査せず終了 0 を返す。型検査は tsc -b（npm run typecheck）で行う。
    npmRun(['run', 'typecheck']);
    done();
  });

  // --- 設計ドキュメント生成 ---

  gulp.task('dev:jig', (done) => {
    gradle(['jigReports']);
    console.log(`\n出力: ${BACKEND_DIR}/<module>/build/jig/index.html（${JIG_MODULE_COUNT} モジュール）`);
    done();
  });

  gulp.task('dev:jig:open', (done) => {
    openUrl(`file://${process.cwd()}/${BACKEND_DIR}/${DEFAULT_SERVICE}/build/jig/index.html`);
    done();
  });

  gulp.task('dev:jig-erd', (done) => {
    gradle(['jigErd']);
    console.log(`\n出力: ${BACKEND_DIR}/<service>/build/jig-erd/*.svg（${DB_SERVICES.length} サービス）`);
    done();
  });

  // --- ヘルプ ---

  gulp.task('dev:help', (done) => {
    console.log(`
アプリケーション開発タスク

  バックエンド
    dev:backend                既定サービス（${DEFAULT_SERVICE}）を起動
    dev:backend:<service>      個別サービスを起動（${SERVICES.join(', ')}）
    dev:backend:build          ビルド（テストを除く）
    dev:backend:test           テスト + カバレッジ
    dev:backend:tdd            TDD モード（テスト自動再実行）
    dev:backend:check          Checkstyle + SpotBugs
    dev:backend:full           フルビルド（ArchUnit とカバレッジ閾値を含む）

  フロントエンド
    dev:frontend               開発サーバー起動（port 5173）
    dev:frontend:build         型検査 + ビルド
    dev:frontend:test          テスト
    dev:frontend:tdd           テスト watch モード
    dev:frontend:lint          ESLint
    dev:frontend:typecheck     型検査（tsc -b）

  設計ドキュメント生成
    dev:jig                    JIG でコードから設計ドキュメントを生成（${JIG_MODULE_COUNT} モジュール）
    dev:jig:open               JIG ドキュメント（${DEFAULT_SERVICE}）をブラウザで開く
    dev:jig-erd                jig-erd で実スキーマから ER 図を生成（Docker + Graphviz 必要）

  docs/design は「こう設計した」、JIG / jig-erd の出力は「こう実装されている」を示す。
  両者を突き合わせて設計と実装の乖離を検出する。

  環境（ミドルウェア・kind クラスタ・運用照会）のタスクは
    npx gulp --tasks-simple で一覧できる（k8s:* / ops:* / projection:* など）。
`);
    done();
  });
}
