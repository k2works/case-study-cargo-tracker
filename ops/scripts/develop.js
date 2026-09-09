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

export const BACKEND_DIR = 'apps/cargo-tracker/backend';
const FRONTEND_DIR = 'apps/cargo-tracker/frontend';

/** 既定で起動するバックエンドサービス。 */
const DEFAULT_SERVICE = 'bookingms';

/** 業務サービス（architecture_backend.md）。 */
export const SERVICES = [
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

/**
 * ポータルに載せる JIG の対象。
 *
 * <p>テスト専用の 2 つ（contract-tests・acceptance-tests）は載せない。読み手が
 * 探しているのは業務の構造であり、テストの足場が並ぶと見つけにくくなる。</p>
 */
export const JIG_SERVICES = ['shared', ...SERVICES];

/** 専用データベースを持つサービス。jig-erd の ER 図はこの単位で生成される。 */
export const DB_SERVICES = ['authms', 'bookingms', 'routingms', 'trackingms', 'handlingms', 'billingms'];

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

/**
 * すでに走っている Gradle ビルドがあれば、始める前に断る。
 *
 * <p>この環境では Gradle を 2 本同時に走らせると build ディレクトリが壊れる。
 * IT11 では 4 回起きた（`EOFException`・`in-progress-results-generic.bin` の
 * `NoSuchFileException`）。そのたびに `rm -rf <module>/build` からやり直す。</p>
 *
 * <p><b>手順書の文章では守れなかった。</b> 「重い検証は 1 本ずつ」は IT9・IT10・
 * IT11 と 3 回 Try に挙がって 3 回とも破られている。守れない約束は、守らせる
 * 仕組みに変える——ここを通らない限りビルドが始まらないようにする。</p>
 *
 * <p>探すのは<b>デーモンではなくクライアント</b>（`GradleWrapperMain` /
 * `GradleMain`）である。デーモンは呼び出しをまたいで生き残るので、その存在は
 * 「いま走っている」ことを意味しない。クライアントはビルドの間だけ生きる。</p>
 *
 * @throws {Error} 別の Gradle ビルドが走っているとき
 */
export function assertNoRunningGradle() {
  // Windows の tasklist は引数まで出さないので、この検出は POSIX のみ。
  // 断れない環境で黙って通すが、通した事実は出す（黙って素通りさせない）。
  if (process.platform === 'win32') {
    console.warn('[gradle] Windows では実行中ビルドを判別できません。1 本ずつ回してください。');
    return;
  }
  const ps = spawnSync('ps', ['-Ao', 'pid=,command='], { encoding: 'utf8' });
  if (ps.status !== 0) {
    throw new Error('実行中の Gradle を確認できませんでした（ps が失敗）。安全側に倒して中止します。');
  }
  const running = ps.stdout
    .split('\n')
    // ラッパーは `java -jar gradle-wrapper.jar` として起こされるのでクラス名は
    // 出ない。`-Dorg.gradle.appname` はクライアントだけが持つ印である。
    .filter((line) => /-Dorg\.gradle\.appname=|GradleWrapperMain|org\.gradle\.launcher\.GradleMain/.test(line))
    .filter((line) => !line.includes('GradleDaemon'));
  if (running.length > 0) {
    throw new Error(
      `Gradle ビルドが ${running.length} 本走っています。終わるまで待ってください。\n`
      + running.map((line) => `  ${line.trim()}`).join('\n')
      + '\n\n同時に走らせると build ディレクトリが壊れ、rm -rf からやり直しになります。',
    );
  }
}

const gradle = (args, extraEnv = {}) => {
  assertNoRunningGradle();
  return run(gradlew, args, BACKEND_DIR, extraEnv);
};

/**
 * 分割フルビルドの単位。
 *
 * <p><b>この環境は 10 分を超える Gradle を完走させない。</b> 通しの `build` は
 * 必ず途中で殺されるので、依存の順に 5 つへ割って 1 群ずつ回す。順序は
 * `settings.gradle.kts` の依存方向——`shared` が全 BC の土台で、テスト専用の
 * 2 つは全サービスを参照するので最後に置く。</p>
 */
export const BUILD_GROUPS = [
  ['shared'],
  ['authms', 'gatewayms'],
  ['bookingms', 'routingms'],
  ['trackingms', 'handlingms'],
  ['billingms', 'contract-tests', 'acceptance-tests'],
];
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

  /**
   * 分割フルビルド。**群ごとに実行中ビルドを断り直す**（途中で別の Gradle が
   * 始まっても、次の群の入口で止まる）。
   *
   * <p>`TZ=UTC` で回す。業務タイムゾーンを導入したあと、テストが JVM 既定の
   * `now()` を使っていると CI（UTC）だけが落ちる。ここで先に落とす。</p>
   */
  gulp.task('dev:backend:full:split', (done) => {
    BUILD_GROUPS.forEach((group, index) => {
      console.log(`\n[${index + 1}/${BUILD_GROUPS.length}] ${group.join(' ')}`);
      gradle(group.map((module) => `:${module}:build`), { TZ: 'UTC' });
    });
    done();
  });

  /**
   * 1 つのモジュールのテストを、名前で絞って回す（TDD の赤緑）。
   *
   *   npx gulp dev:backend:one --module trackingms --tests '*DeadLetterQueueIT*'
   *
   * <p><b>`./gradlew` を直に叩かないための入口である。</b> 直に叩くとこの
   * ガードを通らないので、走っているビルドがあっても始まってしまう。絞り込みが
   * できないと結局は直に叩くことになるので、絞り込みごとここに置く。</p>
   */
  gulp.task('dev:backend:one', (done) => {
    const valueOf = (flag) => {
      const index = process.argv.indexOf(flag);
      return index > -1 ? process.argv[index + 1] : undefined;
    };
    const module = valueOf('--module');
    const tests = valueOf('--tests');
    if (!module) {
      done(new Error("--module <モジュール名> が要ります（例: --module trackingms）"));
      return;
    }
    const args = [`:${module}:test`];
    if (tests) {
      args.push('--tests', tests);
    }
    gradle(args, { TZ: 'UTC' });
    done();
  });

  /** 走っている Gradle があるかだけを見る（回す前の確認用）。 */
  gulp.task('dev:backend:guard', (done) => {
    assertNoRunningGradle();
    console.log('走っている Gradle はありません。');
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
    dev:backend:full:split     分割フルビルド（TZ=UTC・5 群。10 分超が完走しない環境用）
    dev:backend:one            1 モジュールのテストを名前で絞って回す（--module / --tests）
    dev:backend:guard          走っている Gradle があるかを見るだけ

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
