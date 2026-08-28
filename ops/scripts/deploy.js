'use strict';

/**
 * 開発環境（Heroku Container Registry / Runtime）へのデプロイタスク（deploy:dev:*）
 *
 * 手順は docs/operation/開発環境セットアップ手順書.md に対応する。
 * 環境への操作はここに定義したタスクを使い、使い捨てスクリプトを別途書かない。
 */

import { spawnSync } from 'child_process';
import { existsSync } from 'fs';
import { dirname, join } from 'path';
import { cleanDockerEnv, gradleCommand } from './shared.js';

const BACKEND_DIR = 'apps/backend';
const FRONTEND_DIR = 'apps/frontend';

/** バックエンドサービスとローカルポート。Heroku では $PORT が注入される。 */
const BACKEND_SERVICES = [
  'gatewayms',
  'authms',
  'bookingms',
  'routingms',
  'trackingms',
  'handlingms',
  'billingms',
];

/** RabbitMQ（CloudAMQP）を使うサービス。イベントの publish / subscribe を行う。 */
const MESSAGING_SERVICES = ['bookingms', 'trackingms', 'handlingms', 'billingms'];

/** CloudAMQP アドオンを保持するサービス。ここから接続情報を他サービスへ配布する。 */
const AMQP_PRIMARY = 'bookingms';

const ALL_SERVICES = [...BACKEND_SERVICES, 'frontend'];

/**
 * Heroku 512MB dyno に収めるための JVM 設定。
 * 既定の MaxRAMPercentage=75 のままだと R14（Memory quota exceeded）になる。
 */
const JAVA_OPTS =
  '-XX:MaxRAMPercentage=50.0 -XX:ReservedCodeCacheSize=64m -XX:MaxMetaspaceSize=128m' +
  ' -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Tokyo';

function appPrefix() {
  const prefix = process.env.DEV_HEROKU_APP_PREFIX;
  if (!prefix) {
    throw new Error(
      '.env に DEV_HEROKU_APP_PREFIX を設定してください（例: DEV_HEROKU_APP_PREFIX=ct）',
    );
  }
  return prefix;
}

const appName = (service) => `${appPrefix()}-${service}`;

/** アプリ名から URL を組み立てない。実 URL の取得は appUrl() が Heroku に問い合わせる。 */
const urlCache = new Map();

/**
 * Heroku アプリの実 URL を取得する。
 *
 * `https://{app}.herokuapp.com` とは組み立てられない。現在の Heroku は
 * `https://{app}-{ランダム}.herokuapp.com` を割り当てるため、組み立てた URL は
 * 404 になる（実測）。サービス間ルーティングに誤った URL を配ると、
 * 各サービス自体は健全なのに Gateway 経由の呼び出しだけが失敗する。
 */
function appUrl(service) {
  const app = appName(service);
  if (!urlCache.has(app)) {
    const json = capture('heroku', ['apps:info', '-a', app, '--json']);
    const webUrl = JSON.parse(json).app.web_url;
    // 末尾のスラッシュを落とす（`${url}/api/...` の二重スラッシュを避ける）
    urlCache.set(app, webUrl.replace(/\/$/, ''));
  }
  return urlCache.get(app);
}

/**
 * Windows shell に渡す引数を引用する。
 *
 * npm.cmd / heroku.cmd / gradlew.bat は Windows では shell 経由で実行する必要がある。
 * そのまま spawnSync(command, args, { shell: true }) に渡すと、空白を含む
 * `JAVA_OPTS=...` などが複数引数に分割されるため、明示的に command line を組み立てる。
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
 * npm / npx / heroku は .cmd shim を明示しないと、cwd 配下の node_modules を
 * npm 本体として誤解決することがある。
 *
 * @param {string} command コマンド
 * @returns {string} Windows shell に渡すコマンド
 */
function windowsCommand(command) {
  const normalized = String(command).replace(/^"|"$/g, '');
  if (['npm', 'npx', 'heroku'].includes(normalized)) {
    return `${normalized}.cmd`;
  }
  if (normalized === 'curl') {
    return 'curl.exe';
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
    env: {
      ...cleanDockerEnv(),
      // Heroku Container Runtime は x86_64 のみサポートするため、
      // Apple Silicon でも linux/amd64 でビルドする。
      DOCKER_DEFAULT_PLATFORM: process.env.DEV_DOCKER_PLATFORM ?? 'linux/amd64',
      ...extraEnv,
    },
  });
  if (result.status !== 0) {
    throw new Error(`${command} ${args.join(' ')} が終了コード ${result.status} で失敗しました`);
  }
}

function capture(command, args) {
  const result = spawnCommand(command, args, {
    encoding: 'utf8',
    env: cleanDockerEnv(),
  });
  if (result.status !== 0) {
    throw new Error(`${command} ${args.join(' ')} が失敗しました: ${result.stderr}`);
  }
  return result.stdout.trim();
}

const heroku = (args) => run('heroku', args);

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

const npm = (args) => run(process.execPath, [npmCliPath(), ...args], FRONTEND_DIR);

const backendServiceCleanTasks = () => BACKEND_SERVICES.map((service) => `:${service}:clean`);

function serviceDir(service) {
  return service === 'frontend' ? FRONTEND_DIR : `${BACKEND_DIR}/${service}`;
}

/**
 * 標準出力を破棄するデバイス名を返す。
 *
 * Windows には /dev/null が無いため、curl の出力先にそのまま渡すと
 * 「指定されたパスが見つかりません」で失敗する。
 *
 * @returns {string} OS ごとの null device
 */
function nullDevice() {
  return process.platform === 'win32' ? 'NUL' : '/dev/null';
}

export default function (gulp) {
  // --- アプリ作成 ---

  gulp.task('deploy:dev:app:create', (done) => {
    ALL_SERVICES.forEach((service) => {
      // 既存アプリがあってもデプロイを止めない
      const result = spawnCommand('heroku', ['create', appName(service), '--stack', 'container'], {
        stdio: 'inherit',
        env: cleanDockerEnv(),
      });
      if (result.status !== 0) {
        console.log(`  ${appName(service)} は作成済みか作成に失敗しました。続行します。`);
      }
    });
    done();
  });

  // --- Config Vars ---

  gulp.task('deploy:dev:config', (done) => {
    BACKEND_SERVICES.forEach((service) => {
      heroku([
        'config:set',
        'SPRING_PROFILES_ACTIVE=product',
        `JAVA_OPTS=${JAVA_OPTS}`,
        '-a',
        appName(service),
      ]);
    });

    // bookingms は経路の確定時に routingms へ、見積の試算で billingms へ問い合わせる
    // （ADR-019 決定 2・[ADR-028] 決定 6）。渡さないと起動時に落ちる
    // ——**既定値を持たせない**ことで、設定漏れをその場で表面化させる
    heroku([
      'config:set',
      `APP_ROUTING_SERVICE_BASE_URL=${appUrl('routingms')}`,
      `APP_BILLING_SERVICE_BASE_URL=${appUrl('billingms')}`,
      '-a',
      appName('bookingms'),
    ]);

    // Gateway には各サービスのルーティング先を渡す
    heroku([
      'config:set',
      `AUTHMS_URL=${appUrl('authms')}`,
      `BOOKINGMS_URL=${appUrl('bookingms')}`,
      `ROUTINGMS_URL=${appUrl('routingms')}`,
      `TRACKINGMS_URL=${appUrl('trackingms')}`,
      `HANDLINGMS_URL=${appUrl('handlingms')}`,
      `BILLINGMS_URL=${appUrl('billingms')}`,
      `CORS_ALLOWED_ORIGINS=${appUrl('frontend')}`,
      '-a',
      appName('gatewayms'),
    ]);
    done();
  });

  // --- CloudAMQP ---

  gulp.task('deploy:dev:amqp:setup', (done) => {
    const primaryApp = appName(AMQP_PRIMARY);
    const result = spawnCommand('heroku', ['addons:create', 'cloudamqp', '-a', primaryApp], {
      stdio: 'inherit',
      env: cleanDockerEnv(),
    });
    if (result.status !== 0) {
      console.log('  CloudAMQP アドオンは追加済みか、追加に失敗しました。共有処理を続行します。');
    }
    done();
  });

  gulp.task('deploy:dev:amqp:share', (done) => {
    const cloudAmqpUrl = capture('heroku', [
      'config:get',
      'CLOUDAMQP_URL',
      '-a',
      appName(AMQP_PRIMARY),
    ]);
    if (!cloudAmqpUrl) {
      throw new Error(
        `${appName(AMQP_PRIMARY)} に CLOUDAMQP_URL がありません。deploy:dev:amqp:setup を先に実行してください。`,
      );
    }

    // Spring Boot は CLOUDAMQP_URL 単一変数から spring.rabbitmq.* を組み立てない。
    // 個別変数に分解しないと localhost:5672 にフォールバックして接続に失敗する。
    const url = new URL(cloudAmqpUrl);
    const sslEnabled = url.protocol === 'amqps:';
    const vars = [
      `CLOUDAMQP_URL=${cloudAmqpUrl}`,
      `RABBITMQ_HOST=${url.hostname}`,
      `RABBITMQ_PORT=${url.port || (sslEnabled ? '5671' : '5672')}`,
      `RABBITMQ_USERNAME=${decodeURIComponent(url.username)}`,
      `RABBITMQ_PASSWORD=${decodeURIComponent(url.password)}`,
      `RABBITMQ_VIRTUAL_HOST=${url.pathname.replace(/^\//, '')}`,
      `RABBITMQ_SSL_ENABLED=${sslEnabled}`,
    ];

    MESSAGING_SERVICES.forEach((service) => {
      heroku(['config:set', ...vars, '-a', appName(service)]);
    });
    done();
  });

  gulp.task('deploy:dev:amqp:info', (done) => {
    MESSAGING_SERVICES.forEach((service) => {
      console.log(`\n--- ${appName(service)} ---`);
      const config = capture('heroku', ['config', '-a', appName(service)]);
      config
        .split('\n')
        .filter((line) => line.includes('RABBITMQ'))
        .forEach((line) => console.log(line));
    });
    done();
  });

  // --- ビルド・デプロイ ---

  gulp.task('deploy:dev:build', (done) => {
    // 古い成果物が残っていると Dockerfile の COPY build/libs/*.jar が
    // 複数の jar を拾って失敗する（plain jar 無効化前のビルド成果物など）。
    run(
      gradleCommand(BACKEND_DIR),
      ['--no-daemon', ...backendServiceCleanTasks(), 'bootJar', '-x', 'test'],
      BACKEND_DIR,
    );
    npm(['run', 'build']);
    done();
  });

  /**
   * Heroku Container Registry へイメージをプッシュする。
   *
   * `heroku container:push` は使わない。Docker Desktop の containerd image store が
   * 有効な環境ではイメージが OCI マニフェストで作られ、Heroku Registry が
   * `error from registry: unsupported` で拒否するためである（実測）。
   *
   * buildx で `oci-mediatypes=false` を明示し、Docker Image Manifest V2 で
   * レジストリへ直接プッシュする。`provenance=false` も必須で、
   * 付けるとアテステーション用のマニフェストリストが作られ同じく拒否される。
   */
  function pushImage(service) {
    run(
      'docker',
      [
        'buildx',
        'build',
        '--platform',
        process.env.DEV_DOCKER_PLATFORM ?? 'linux/amd64',
        '--provenance=false',
        // 開発環境のフロントエンドでは動作確認用ログインの事前入力を有効にする。
        // バックエンドのビルドでは未定義の引数として無視される
        ...(service === 'frontend'
          ? [
            '--build-arg', 'VITE_DEMO_LOGIN_ENABLED=true',
            // Heroku ではフロントと Gateway が別オリジンになる。同一オリジンの
            // /api を叩く既定のままだと、画面は API に到達できず
            // 「サーバーに接続できませんでした」としか言えない
            '--build-arg', `VITE_API_BASE_URL=${appUrl('gatewayms').replace(/\/$/, '')}`,
          ]
          : []),
        '--output',
        `type=registry,oci-mediatypes=false,name=registry.heroku.com/${appName(service)}/web:latest`,
        '.',
      ],
      serviceDir(service),
    );
  }

  ALL_SERVICES.forEach((service) => {
    gulp.task(`deploy:dev:push:${service}`, (done) => {
      pushImage(service);
      done();
    });

    gulp.task(`deploy:dev:release:${service}`, (done) => {
      run('heroku', ['container:release', 'web', '-a', appName(service)], serviceDir(service));
      done();
    });
  });

  gulp.task('deploy:dev:push', (done) => {
    ALL_SERVICES.forEach((service) => {
      pushImage(service);
    });
    done();
  });

  gulp.task('deploy:dev:release', (done) => {
    ALL_SERVICES.forEach((service) => {
      run('heroku', ['container:release', 'web', '-a', appName(service)], serviceDir(service));
    });
    done();
  });

  gulp.task(
    'deploy:dev',
    gulp.series('deploy:dev:build', 'deploy:dev:push', 'deploy:dev:release'),
  );

  gulp.task(
    'deploy:dev:setup',
    gulp.series(
      'deploy:dev:app:create',
      'deploy:dev:config',
      'deploy:dev:amqp:setup',
      'deploy:dev:amqp:share',
      'deploy:dev:build',
      'deploy:dev:push',
      'deploy:dev:release',
    ),
  );

  // --- 確認・ロールバック ---

  gulp.task('deploy:dev:status', (done) => {
    ALL_SERVICES.forEach((service) => {
      console.log(`\n--- ${appName(service)} ---`);
      const result = spawnCommand('heroku', ['ps', '-a', appName(service)], {
        stdio: 'inherit',
        env: cleanDockerEnv(),
      });
      if (result.status !== 0) {
        console.log('  状態を取得できませんでした');
      }
    });
    done();
  });

  gulp.task('deploy:dev:health', (done) => {
    BACKEND_SERVICES.forEach((service) => {
      console.log(`${appUrl(service)}/actuator/health`);
    });
    done();
  });

  ALL_SERVICES.forEach((service) => {
    gulp.task(`deploy:dev:rollback:${service}`, (done) => {
      heroku(['releases:rollback', '-a', appName(service)]);
      done();
    });
  });

  // ============================================
  // ドキュメントサイト（ポータル / docs / マニュアル / JIG / jig-erd）
  //
  // マニュアルは apps/www/manual に出力し、ポータル（apps/www）ごと配信する。
  // 別の場所に出して Dockerfile 側で拾う形にすると、置き場所と配信先が離れ、
  // 「生成したのに読者に届かない」状態を作る（IT2 で実際に起きた）。
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
          '例: DOCS_HEROKU_APP_NAME=take7-docs',
      );
    }
    return name;
  }

  /**
   * 配信する成果物を生成する。
   *
   * **このイメージは配信するだけで生成は行わない。**
   * JIG は JDK、jig-erd は Docker と Graphviz を必要とするため、
   * 生成はホスト側で行い、成果物だけをイメージに載せる。
   */
  gulp.task('deploy:docs:artifacts', gulp.series('mkdocs:build', 'manual:build', 'dev:jig', 'dev:jig-erd'));

  gulp.task('deploy:docs:app:create', (done) => {
    const result = spawnCommand('heroku', ['create', docsAppName(), '--stack', 'container'], {
      stdio: 'inherit',
      env: cleanDockerEnv(),
    });
    if (result.status !== 0) {
      console.log(`  ${docsAppName()} は作成済みか作成に失敗しました。続行します。`);
    }
    done();
  });

  gulp.task('deploy:docs:registry:login', (done) => {
    run('heroku', ['container:login']);
    done();
  });

  gulp.task('deploy:docs:push', (done) => {
    // push の方式は deploy:dev:push と同じ理由（Heroku Registry が OCI マニフェスト非対応）。
    run('docker', [
      'buildx',
      'build',
      '--platform',
      process.env.DEV_DOCKER_PLATFORM ?? 'linux/amd64',
      '--provenance=false',
      '--sbom=false',
      '-f',
      'ops/docker/docs-site/Dockerfile',
      '--output',
      `type=image,name=registry.heroku.com/${docsAppName()}/web:latest,push=true,oci-mediatypes=false`,
      '.',
    ]);
    done();
  });

  gulp.task('deploy:docs:release', (done) => {
    run('heroku', ['container:release', 'web', '-a', docsAppName()]);
    done();
  });

  /**
   * ポータルとリンク先が実際に配信されているかを確認する。
   *
   * **ポータルが表示されただけでは、リンク先が見えているとは限らない。**
   * ポータルからのリンク先をそのまま検証対象にしている。
   */
  gulp.task('deploy:docs:verify', (done) => {
    const json = capture('heroku', ['apps:info', '-a', docsAppName(), '--json']);
    const webUrl = JSON.parse(json).app.web_url.replace(/\/$/, '');

    const paths = [
      '/healthz',
      '/',
      '/style.css',
      '/docs/',
      '/docs/design/',
      '/docs/requirements/',
      '/docs/adr/',
      '/docs/operation/',
      '/jig/',
      '/jig-erd/',
      '/manual/',
      // サービス単位の生成物も 1 つずつ確認する。
      // 一覧ページが 200 でも、リンク先が欠けていることがあるため。
      '/jig/bookingms/',
      '/jig/shared/',
      '/jig-erd/bookingms/bookingms-erd-summary.svg',
      '/jig-erd/authms/authms-erd-summary.svg',
      // ユーザーマニュアルは実体（章とキャプチャ）まで見る。
      // 入口の 200 だけを見ていたため、「まだ作成していません」という古い
      // プレースホルダが配信されたままなのに緑になっていた（IT2 で発覚）。
      // マニュアルは業務担当者が画面の前で読む唯一の手引きであり、
      // 届いていなければ書いていないのと変わらない。
      '/manual/index.html',
      '/manual/01-業務フロー.html',
      '/manual/04-貨物予約.html',
      '/manual/assets/04-booking-register.png',
      '/manual/style.css',
    ];

    const failed = [];
    for (const p of paths) {
      const code = capture('curl', [
        '-s',
        '-o',
        nullDevice(),
        '-w',
        '%{http_code}',
        '--max-time',
        '30',
        `${webUrl}${p}`,
      ]);
      console.log(`  ${code}  ${webUrl}${p}`);
      if (code !== '200') {
        failed.push(`${p} が ${code}`);
      }
    }
    if (failed.length > 0) {
      throw new Error(`ドキュメントサイトの検証に失敗しました: ${failed.join(', ')}`);
    }
    console.log('\nドキュメントサイトのデプロイを確認しました');
    console.log(`  ${webUrl}`);
    done();
  });

  gulp.task('deploy:docs:open', (done) => {
    run('heroku', ['open', '-a', docsAppName()]);
    done();
  });

  gulp.task('deploy:docs:logs', (done) => {
    run('heroku', ['logs', '-n', '100', '-a', docsAppName()]);
    done();
  });

  gulp.task(
    'deploy:docs',
    gulp.series(
      'deploy:docs:artifacts',
      'deploy:docs:registry:login',
      'deploy:docs:push',
      'deploy:docs:release',
      'deploy:docs:verify',
    ),
  );

  gulp.task('deploy:docs:help', (done) => {
    console.log(`
ドキュメントサイトデプロイタスク（Heroku）

  前提: heroku login && heroku container:login を実行済みであること
        .env に DOCS_HEROKU_APP_NAME を設定していること
        JIG に JDK、jig-erd に Docker と Graphviz が必要

  初回セットアップ
    deploy:docs:app:create      Heroku アプリを作成（container stack）
    deploy:docs                 生成 → push → release → 検証

  個別操作
    deploy:docs:artifacts       MkDocs / マニュアル / JIG / jig-erd を生成
    deploy:docs:registry:login  Heroku Container Registry へログイン
    deploy:docs:push            イメージをビルドして Registry へ push
    deploy:docs:release         リリース
    deploy:docs:verify          ポータルとリンク先が配信されているか確認
    deploy:docs:open            ブラウザで開く
    deploy:docs:logs            ログを表示

  配信内容
    /            ポータル（apps/www）
    /docs/       MkDocs（設計・要件・運用）
    /jig/        JIG（8 モジュール）
    /jig-erd/    ER 図（専用 DB を持つ 6 サービス）
    /manual/     ユーザーマニュアル（docs/manual の Markdown を manual:build が
                 HTML へ変換し apps/www/manual へ出力したもの。ポータルごと配信する）
`);
    done();
  });

  gulp.task('deploy:dev:help', (done) => {
    console.log(`
開発環境（Heroku）デプロイタスク

  前提: heroku login && heroku container:login を実行済みであること
        .env に DEV_HEROKU_APP_PREFIX を設定していること

  初回セットアップ
    deploy:dev:setup            アプリ作成 → Config Vars → CloudAMQP → ビルド → push → release

  更新デプロイ
    deploy:dev                  ビルド → push → release（全サービス）
    deploy:dev:push:<service>   特定サービスを push
    deploy:dev:release:<service> 特定サービスをリリース

  個別操作
    deploy:dev:app:create       Heroku アプリを作成（container stack）
    deploy:dev:config           Config Vars を設定（profile / JAVA_OPTS / ルーティング）
    deploy:dev:amqp:setup       CloudAMQP アドオンを追加
    deploy:dev:amqp:share       CLOUDAMQP_URL を個別変数に分解して配布
    deploy:dev:amqp:info        RabbitMQ 関連の Config Vars を表示

  確認・切り戻し
    deploy:dev:status           全サービスの dyno 状態
    deploy:dev:health           ヘルスチェック URL を表示
    deploy:dev:rollback:<service> 直前のリリースに戻す

  サービス名: ${ALL_SERVICES.join(', ')}
`);
    done();
  });
}
