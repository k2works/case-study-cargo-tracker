'use strict';

/**
 * cargo-tracker の運用タスク。
 *
 * 手順書: docs/design/cargo-tracker/operation.md
 *
 * ここに置くのは「運用手順書に載っている操作」だけ。調査用の使い捨てはスクラッチパッドに置く。
 */

import { execSync, spawn } from 'child_process';
import fs from 'fs';
import path from 'path';
import { openUrl } from './shared.js';
import { BACKEND_DIR, DB_SERVICES, JIG_SERVICES } from './develop.js';

const COMPOSE_DIR = 'apps/cargo-tracker';

/** サービスと待ち受けポート（architecture_backend.md）。 */
const SERVICES = {
  gatewayms: 8080,
  authms: 8081,
  bookingms: 8082,
  routingms: 8083,
  trackingms: 8084,
  handlingms: 8085,
  billingms: 8086,
};

function sh(command, options = {}) {
  return execSync(command, { stdio: 'pipe', encoding: 'utf8', ...options });
}

/**
 * curl に本文を捨てさせる先。
 *
 * Windows に `/dev/null` は無く、渡すと curl が書き込みエラー（23）で落ちる。
 * 生死の判定がすべて `000` に倒れ、「動いていない」と誤って報告することになる。
 */
const NULL_DEVICE = process.platform === 'win32' ? 'NUL' : '/dev/null';

function tryFetch(url) {
  try {
    return sh(`curl -sS -m 5 -o ${NULL_DEVICE} -w %{http_code} ${url}`).trim();
  } catch {
    return '000';
  }
}

/**
 * k8s に配るフロントエンド。SERVICES と分けているのは、フロントに
 * /actuator/health が無く、ops:health のポート表と同じ形で扱えないためである。
 */
const FRONTEND = { name: 'frontend', dir: 'frontend' };

/**
 * ドキュメントポータル。ビルド工程を持たず、生成済みの静的ファイルを配るだけ。
 * apps/cargo-tracker の外に置いてあるので COMPOSE_DIR を使わない。
 */
const PORTAL = { name: 'www', dir: 'apps/cargo-tracker/www' };

/** k8s:open が転送する先。クラスタに Ingress が無いので、ホストからはここだけが見える。 */
const GATEWAY_PORT = 8080;
const FRONTEND_PORT = 3000;
const PORTAL_PORT = 3001;
const FORWARDS = [
  // フロントは自分の nginx で /api を Gateway に中継するので、画面を触るだけなら
  // ここ 1 つで足りる。5173 を避けているのは、ローカルの Vite 開発サーバと
  // 取り違えないためである。
  { label: 'フロントエンド        ', service: 'frontend', port: FRONTEND_PORT, target: 8080 },
  { label: 'API Gateway        ', service: 'gatewayms', port: GATEWAY_PORT },
  // ポータルはフロントの nginx が /docs-portal/ で中継するので、画面から
  // 辿るだけならこの転送は要らない。直接開く場合と、Vite 開発サーバ
  // （5173）から中継させる場合のために張っておく。
  { label: 'ドキュメントポータル  ', service: 'www', port: PORTAL_PORT, target: 80 },
  { label: 'Axon Server コンソール', service: 'axonserver', port: 8024 },
];

/** ポートが既に使われているか。docker compose と取り合うのを先に見つける。 */
function isPortInUse(port) {
  try {
    execSync(`lsof -i :${port} -sTCP:LISTEN -t`, { stdio: 'pipe' });
    return true;
  } catch {
    return false;
  }
}

/** port-forward が張れて Gateway が応答するまで待つ（最大 30 秒）。 */
async function waitForGateway() {
  for (let i = 0; i < 30; i += 1) {
    if (tryFetch(`http://localhost:${GATEWAY_PORT}/actuator/health`) === '200') {
      return true;
    }
    await new Promise((r) => setTimeout(r, 1000));
  }
  return false;
}

/**
 * 入口を持たない生成物（SVG の並び）に、ファイル一覧の index.html を作る。
 *
 * @param {string} dir 対象ディレクトリ
 * @param {string} service サービス名
 * @param {string} title 種類の名前
 */
function writeFileIndex(dir, service, title) {
  const files = fs.readdirSync(dir).filter((f) => f !== 'index.html').sort();
  const links = files
    .map((file) => `      <li><a href="${file}">${file}</a></li>`)
    .join('\n');
  fs.writeFileSync(path.join(dir, 'index.html'), `<!DOCTYPE html>
<html lang="ja">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${service} — ${title}</title>
<link rel="stylesheet" href="../../style.css">
</head>
<body>
<header class="hero"><div class="hero-inner">
  <p class="eyebrow">生成ドキュメント</p>
  <h1>${service}</h1>
  <p class="lead">${title}。概要・サマリー・詳細の 3 枚が出ます。</p>
</div></header>
<main>
  <section>
    <ul>
${links}
    </ul>
    <p class="section-note"><a href="../">サービスの一覧へ戻る</a></p>
  </section>
</main>
</body>
</html>
`, 'utf8');
}

/**
 * ビルドディレクトリに出た生成物をサービスごとにポータル配下へ写し、入口を作る。
 *
 * <p>1 つも見つからないときは失敗させる。黙って空の入口を置くと、ポータルには
 * リンクがあるのに開くと 404 という状態が、生成を忘れた側から見えなくなる。</p>
 *
 * @param {string} kind 生成物の種類（build/<kind> と www/<kind> に対応）
 * @param {string[]} services 対象サービス
 * @param {string} title 入口ページの見出し
 * @param {string} description 入口ページの説明
 * @param {(error?: Error) => void} done Gulp のコールバック
 */
function copyGenerated(kind, services, title, description, done) {
  const outDir = path.join(PORTAL.dir, kind);
  fs.rmSync(outDir, { recursive: true, force: true });
  fs.mkdirSync(outDir, { recursive: true });

  const copied = services.filter((service) => {
    const from = path.join(BACKEND_DIR, service, 'build', kind);
    if (!fs.existsSync(from)) {
      return false;
    }
    const to = path.join(outDir, service);
    fs.cpSync(from, to, { recursive: true });
    // jig-erd は SVG を並べるだけで入口を作らない。作らないと、
    // ディレクトリ一覧を返さない nginx では開いた瞬間 403/404 になる。
    if (!fs.existsSync(path.join(to, 'index.html'))) {
      writeFileIndex(to, service, title);
    }
    return true;
  });

  if (copied.length === 0) {
    done(new Error(`${kind} の出力がありません。先に生成タスクを実行してください`));
    return;
  }

  const links = copied
    .map((service) => `      <li><a href="${service}/">${service}</a></li>`)
    .join('\n');
  fs.writeFileSync(path.join(outDir, 'index.html'), `<!DOCTYPE html>
<html lang="ja">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${title} — Cargo Tracker</title>
<link rel="stylesheet" href="../style.css">
</head>
<body>
<header class="hero"><div class="hero-inner">
  <p class="eyebrow">生成ドキュメント</p>
  <h1>${title}</h1>
  <p class="lead">${description}</p>
</div></header>
<main>
  <section>
    <h2>サービスを選ぶ</h2>
    <ul>
${links}
    </ul>
    <p class="section-note"><a href="../">ポータルへ戻る</a></p>
  </section>
</main>
</body>
</html>
`, 'utf8');

  console.log(`${kind}: ${copied.length} 件を ${outDir} に置きました`);
  done();
}

/** kind クラスタ名と名前空間。手順書と揃える。 */
const KIND_CLUSTER = 'cargo-tracker';
const NAMESPACE = 'cargo-tracker';
const OVERLAY = 'ops/k8s/overlays/local';

export default function (gulp) {
  /**
   * MkDocs の出力（site/）をポータル配下へ置く。
   *
   * <p>生成物はコミットしない。コミットすると「コードを変えたのに図が古い」
   * 状態がリポジトリに固定される。配るたびに作り直す。</p>
   */
  gulp.task('portal:docs', (done) => {
    const site = 'site';
    if (!fs.existsSync(site)) {
      done(new Error('site/ がありません。npx gulp mkdocs:build を先に実行してください'));
      return;
    }
    const out = path.join(PORTAL.dir, 'docs');
    fs.rmSync(out, { recursive: true, force: true });
    fs.cpSync(site, out, { recursive: true });
    console.log(`${site} を ${out} に置きました`);
    done();
  });

  /**
   * JIG（バイトコードから起こした設計ドキュメント）をポータル配下へ置く。
   *
   * <p>サービスごとに 1 つずつ出るので、入口の index.html を生成して束ねる。
   * サービス名の一覧を手書きの HTML に持たせると、サービスを増やしたときに
   * 片方だけ直され、リンクの無いサービスができる。</p>
   */
  gulp.task('portal:jig', (done) => {
    copyGenerated('jig', JIG_SERVICES, 'JIG',
      'バイトコードから起こしたドメインモデル・パッケージ関連・用語集。'
      + '突き合わせ先はドメインモデル設計とバックエンドアーキテクチャ。', done);
  });

  /** jig-erd（実スキーマから起こした ER 図）をポータル配下へ置く。 */
  gulp.task('portal:jig-erd', (done) => {
    copyGenerated('jig-erd', DB_SERVICES, 'ER 図',
      'Flyway が実際に構築したスキーマの ER 図。突き合わせ先はデータモデル設計。'
      + 'Database per Service なのでサービス単位に分かれる。', done);
  });

  /** ポータルが配る生成物（ドキュメントサイト・マニュアル・JIG・ER 図）を作り直す。 */
  gulp.task('portal:artifacts', gulp.series(
    'mkdocs:build', 'portal:docs', 'manual:build',
    'dev:jig', 'portal:jig', 'dev:jig-erd', 'portal:jig-erd',
  ));

  /** ポータルをブラウザで開く（k8s:open で転送を張っている間だけ開ける）。 */
  gulp.task('portal:open', (done) => {
    openUrl(`http://localhost:${FRONTEND_PORT}/docs-portal/`);
    done();
  });

  /** マニフェストを描画して構文と参照を確かめる（クラスタが無くても回せる）。 */
  gulp.task('k8s:render', (done) => {
    console.log(sh(`kubectl kustomize ${OVERLAY}`).split('\n').length + ' 行を描画しました');
    done();
  });

  /** kind クラスタを作る。既にあれば作り直さない。 */
  gulp.task('k8s:up', (done) => {
    const clusters = (() => {
      try {
        return sh('kind get clusters');
      } catch {
        return '';
      }
    })();
    if (!clusters.split('\n').includes(KIND_CLUSTER)) {
      console.log(sh(`kind create cluster --name ${KIND_CLUSTER}`, { stdio: 'inherit' }) ?? '');
    }
    // クラスタがあっても kubeconfig に context が無いことがある（作成を途中で
    // 止めた、別の kubeconfig で作った、他のツールに current-context を奪われた）。
    // context は何度書き出しても同じものになるので、存在を確かめずに毎回書く。
    // ここを飛ばすと apply が `context "kind-..." does not exist` で落ち、
    // クラスタが無いのか context が無いのかが読み取れない。
    sh(`kind export kubeconfig --name ${KIND_CLUSTER}`);
    console.log(sh(`kubectl --context kind-${KIND_CLUSTER} apply -k ${OVERLAY}`));
    done();
  });

  /**
   * 全サービスのイメージを作る。
   *
   * <p>ビルドは各サービスの Dockerfile の中で行う（ホストの build/ は
   * .dockerignore で持ち込まない）。ローカルで何を回したかによって
   * イメージの中身が変わるのを防ぐためである。</p>
   */
  gulp.task('k8s:images', (done) => {
    const services = Object.keys(SERVICES);
    const backend = `${COMPOSE_DIR}/backend`;
    const total = services.length + 2;
    services.forEach((name, i) => {
      console.log(`[${i + 1}/${total}] cargo-tracker/${name}:latest`);
      sh(`docker build -t cargo-tracker/${name}:latest -f ${name}/Dockerfile .`,
        { cwd: backend, stdio: 'inherit' });
    });
    // フロントはビルドコンテキストが自分のディレクトリなので、まとめて回さない。
    //
    // 動作確認用の利用者の事前入力（ADR-0004）はここで明示的に有効にする。
    // SPA の設定はビルド時に焼き込まれ、実行時には取り消せない。Dockerfile 側の
    // 既定は無効なので、渡し忘れた成果物は認証情報を持たない。
    console.log(`[${total - 1}/${total}] cargo-tracker/${FRONTEND.name}:latest`);
    sh(`docker build --build-arg VITE_DEMO_LOGIN_ENABLED=true `
      + `-t cargo-tracker/${FRONTEND.name}:latest .`,
      { cwd: `${COMPOSE_DIR}/${FRONTEND.dir}`, stdio: 'inherit' });
    // ポータルは docs / manual を焼き込む。生成してから作らないと、
    // リンクだけあって中身が 404 のポータルができる。
    const missing = ['docs', 'manual', 'jig', 'jig-erd']
      .filter((dir) => !fs.existsSync(path.join(PORTAL.dir, dir)));
    if (missing.length > 0) {
      done(new Error(
        `ポータルの配信物がありません（${missing.join(', ')}）。`
        + ' npx gulp portal:artifacts を先に実行してください',
      ));
      return;
    }
    console.log(`[${total}/${total}] cargo-tracker/${PORTAL.name}:latest`);
    sh(`docker build -t cargo-tracker/${PORTAL.name}:latest .`,
      { cwd: PORTAL.dir, stdio: 'inherit' });
    console.log(`${total} 個のイメージを作りました`);
    done();
  });

  /**
   * ビルドしたイメージを kind に載せる。
   *
   * タグを据え置いたまま載せ直しても Pod は作り直されないので、rollout restart まで踏む。
   * ここを飛ばすと、古いイメージのまま「反映した」と思い込む。
   */
  gulp.task('k8s:load', (done) => {
    const deployments = [...Object.keys(SERVICES), FRONTEND.name, PORTAL.name];
    for (const name of deployments) {
      sh(`kind load docker-image cargo-tracker/${name}:latest --name ${KIND_CLUSTER}`);
      sh(`kubectl --context kind-${KIND_CLUSTER} -n ${NAMESPACE} rollout restart deployment/${name}`);
    }
    console.log(`${deployments.length} 個のイメージを載せ直しました`);
    done();
  });

  /** クラスタ側の Pod の状態。ホストからは何も見えないので、まずここを見る。 */
  gulp.task('k8s:status', (done) => {
    console.log(sh(`kubectl --context kind-${KIND_CLUSTER} -n ${NAMESPACE} get pods -o wide`));
    done();
  });

  /**
   * kind をゼロから立ち上げる（手順をこの順で固定する）。
   *
   * <p>`k8s:up` が踏むのはクラスタ作成とマニフェストの適用だけである。イメージを
   * 作らずに適用すると Pod は ImagePullBackOff で止まるが、`kubectl logs` が
   * 返すのは完了済みの init コンテナが残した「Axon Server を待っています」で、
   * 原因がイメージ側にあることが読み取れない。順序を落とせないようにする。</p>
   *
   * <p>先頭に `portal:artifacts` を置くのは、`k8s:images` がポータルの配信物を
   * 要求するためである。ここを外に出すと、立ち上げのたびに「先に何を回すか」を
   * 思い出す必要が残る。</p>
   *
   * <p>コードを直して反映したいだけなら `k8s:images` → `k8s:load` で足りる。
   * `k8s:up` の適用は繰り返しても安全なので、ここでは分岐させない。</p>
   */
  gulp.task('k8s:setup', gulp.series(
    'portal:artifacts', 'k8s:images', 'k8s:up', 'k8s:load', 'k8s:status',
  ));

  /**
   * 滞留している連鎖の一覧（ADR-0001 決定 6 / data-model.md）。
   *
   * Axon 5 に Saga（と Deadline）が無いので、止まった連鎖はこのテーブルを
   * 走査して見つける。件数を出すだけでなく、どの段で止まったかまで出す。
   */
  gulp.task('reaction:stuck', (done) => {
    const hours = (() => {
      const i = process.argv.indexOf('--older-than-hours');
      return i > -1 ? Number(process.argv[i + 1]) : 24;
    })();
    try {
      console.log(
        sh(
          `docker exec cargo-tracker-postgres psql -U postgres -d booking_read_db -c ` +
            `"SELECT process_type, process_id, current_step, completed_steps || '/' || total_steps AS steps, ` +
            `updated_at FROM process_state WHERE status = 'RUNNING' ` +
            `AND updated_at < now() - interval '${hours} hours' ORDER BY updated_at"`,
        ),
      );
    } catch (e) {
      console.log(`  読めません: ${e.message.split('\n')[0]}`);
    }
    done();
  });

  /** マニュアルの画面キャプチャを撮り直す（creating-manual）。手で配置しない。 */
  gulp.task('manual:capture', (done) => {
    console.log(sh('npm run manual:capture', { cwd: 'apps/cargo-tracker/frontend', stdio: 'inherit' }) ?? '');
    done();
  });

  /**
   * ローカル統合環境（kind）の画面をブラウザで開く。
   *
   * <p>クラスタには Ingress が無く、Service はすべて ClusterIP である。ホストからは
   * 何も見えないので、port-forward を張ってから開く。</p>
   *
   * <p>port-forward はこのタスクが生きているあいだだけ有効である。ブラウザを開いて
   * すぐ終わると転送も切れるため、Ctrl+C まで待ち続ける。**バックグラウンドに
   * 逃がさない**のは、切り忘れた転送が残ると「どのクラスタを見ているか」が
   * 分からなくなるためである。</p>
   */
  gulp.task('k8s:open', () => new Promise((resolve, reject) => {
    const context = `kind-${KIND_CLUSTER}`;

    // 依存ミドルウェアを docker compose で上げたままだと、同じポートを取り合って
    // port-forward が「address already in use」で落ちる。先に言う。
    const occupied = FORWARDS.filter(({ port }) => isPortInUse(port));
    if (occupied.length > 0) {
      reject(new Error(
        `ポート ${occupied.map((f) => f.port).join(', ')} は使用中です。`
        + ' docker compose down で依存ミドルウェアを止めてから実行してください',
      ));
      return;
    }

    const children = FORWARDS.map(({ service, port, target }) => spawn('kubectl', [
      '--context', context, '-n', NAMESPACE,
      'port-forward', `svc/${service}`, `${port}:${target ?? port}`,
    ], { stdio: 'inherit' }));

    const stopAll = () => children.forEach((child) => child.kill());
    children.forEach((child) => child.on('exit', (code) => {
      if (code !== 0 && code !== null) {
        stopAll();
        reject(new Error(`kubectl port-forward が終了コード ${code} で失敗しました`));
      }
    }));

    waitForGateway()
      .then((ready) => {
        if (!ready) {
          stopAll();
          reject(new Error(
            'Gateway が応答しません。npx gulp k8s:up で適用したか、'
            + ' kubectl -n ' + NAMESPACE + ' get pods で Pod が Running かを確認してください',
          ));
          return;
        }
        FORWARDS.forEach(({ label, port }) => console.log(`${label}\thttp://localhost:${port}`));
        console.log('\nローカルの Vite 開発サーバ（5173）も、/api がこの Gateway に繋がります。');
        console.log('Ctrl+C で転送を終了します。');
        openUrl(`http://localhost:${FRONTEND_PORT}`);
        process.on('SIGINT', () => {
          stopAll();
          resolve();
        });
      })
      .catch((e) => {
        stopAll();
        reject(e);
      });
  }));

  /** kind クラスタを消す。取り消せないので名前を明示する。 */
  gulp.task('k8s:down', (done) => {
    console.log(sh(`kind delete cluster --name ${KIND_CLUSTER}`));
    done();
  });

  /**
   * 依存ミドルウェアとサービスの生死を 1 画面で見る。
   *
   * 「動いているつもり」を潰すのが目的なので、繋がらないものを黙って飛ばさない。
   */
  gulp.task('ops:health', (done) => {
    console.log('=== 依存ミドルウェア ===');
    try {
      console.log(sh(`docker compose -f ${COMPOSE_DIR}/docker-compose.yml ps --format '{{.Name}}\t{{.Status}}'`));
    } catch {
      console.log('  docker compose が動いていません');
    }

    const axon = tryFetch('http://localhost:8024/actuator/health');
    console.log(`Axon Server (8024)   ${axon === '200' ? 'OK' : `NG (${axon})`}`);

    console.log('=== サービス ===');
    let down = 0;
    for (const [name, port] of Object.entries(SERVICES)) {
      const code = tryFetch(`http://localhost:${port}/actuator/health`);
      const ok = code === '200';
      if (!ok) down += 1;
      console.log(`${name.padEnd(12)} ${port}  ${ok ? 'OK' : `NG (${code})`}`);
    }
    if (down > 0) {
      console.log(`\n${down} 件が応答しません。起動していないか、Axon Server への接続検査で止まっています。`);
    }
    done();
  });

  /**
   * 投影の進み具合（Processing Group ごとのトークン位置）。
   *
   * 反映が止まっていることに気づく手段。止まっていたら次はリプレイの手順へ進む。
   */
  gulp.task('projection:status', (done) => {
    const databases = {
      bookingms: 'booking_read_db',
      routingms: 'routing_read_db',
      trackingms: 'tracking_read_db',
      handlingms: 'handling_read_db',
      billingms: 'billing_read_db',
    };
    for (const [service, database] of Object.entries(databases)) {
      console.log(`=== ${service} (${database}) ===`);
      try {
        console.log(
          sh(
            `docker exec cargo-tracker-postgres psql -U postgres -d ${database} -c ` +
              `"SELECT processor_name, segment, owner, timestamp FROM token_entry ORDER BY processor_name, segment"`,
          ),
        );
      } catch (e) {
        console.log(`  読めません: ${e.message.split('\n')[0]}`);
      }
    }
    done();
  });

  /**
   * 荷主の個人情報を削除する（crypto-shredding。ADR-0003）。
   *
   *   npx gulp shipper:shred --shipper <shipperId>
   *
   * 鍵を破棄すると、その荷主の氏名・メール・電話・住所は Event Store からも
   * バックアップからも読めなくなる。取り消せないので、実行前に対象を表示して確認する。
   *
   * 本番は KMS のエイリアス削除と ScheduleKeyDeletion（7 日待機）に置き換わる。
   * その手順は operation.md を正とし、ここでローカルの手順を先に固める。
   */
  gulp.task('shipper:shred', (done) => {
    const index = process.argv.indexOf('--shipper');
    const shipperId = index > -1 ? process.argv[index + 1] : undefined;
    if (!shipperId) {
      done(new Error('--shipper <shipperId> が要ります'));
      return;
    }

    console.log(`対象の荷主: ${shipperId}`);
    try {
      console.log(
        sh(
          `docker exec cargo-tracker-postgres psql -U postgres -d booking_read_db -c ` +
            `"SELECT shipper_id, shipper_code, name, email FROM shipper WHERE shipper_id = '${shipperId}'"`,
        ),
      );
    } catch (e) {
      console.log(`  投影が読めません: ${e.message.split('\n')[0]}`);
    }

    console.log('\n次の手順で削除します（operation.md）。');
    console.log('  1. 鍵を破棄する（ローカルは .keys/shipper/<id>.key を削除）');
    console.log('  2. 投影をリプレイする（個人情報の列が NULL になる）');
    console.log('  3. 投影に残っている個人情報を消す（リプレイまでの間の露出を短くする）');
    console.log('\n取り消せません。実行は手順書の確認を経てから行ってください。');
    done();
  });

  /** kind 上のローカル統合環境の操作一覧。 */
  gulp.task('k8s:help', (done) => {
    console.log(`
=== ローカル統合環境（kind）コマンド ===

  k8s:setup    ゼロから立ち上げる（portal:artifacts -> images -> up -> load -> status）
  k8s:images   9 イメージを作る（7 サービス + フロントエンド + ポータル。初回は 25 分程度）
  k8s:up       kind クラスタを作ってマニフェストを適用する
  k8s:load     イメージを kind に載せ直して rollout restart する
  k8s:status   Pod の状態を表示する
  k8s:render   マニフェストを描画する（クラスタが無くても回せる）
  k8s:open     port-forward を張って画面を開く（Ctrl+C まで待つ）
  k8s:down     kind クラスタを消す
  k8s:help     このヘルプを表示する

手順書: docs/operation/cargo-tracker/アプリケーション開発環境セットアップ手順書.md
`);
    done();
  });
}
