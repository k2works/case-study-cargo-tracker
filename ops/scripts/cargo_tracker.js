'use strict';

/**
 * cargo-tracker の運用タスク。
 *
 * 手順書: docs/design/cargo-tracker/operation.md
 *
 * ここに置くのは「運用手順書に載っている操作」だけ。調査用の使い捨てはスクラッチパッドに置く。
 */

import { execSync, spawn } from 'child_process';
import { openUrl } from './shared.js';

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

function tryFetch(url) {
  try {
    return sh(`curl -sS -m 5 -o /dev/null -w '%{http_code}' ${url}`).trim();
  } catch {
    return '000';
  }
}

/**
 * k8s に配るフロントエンド。SERVICES と分けているのは、フロントに
 * /actuator/health が無く、ops:health のポート表と同じ形で扱えないためである。
 */
const FRONTEND = { name: 'frontend', dir: 'frontend' };

/** k8s:open が転送する先。クラスタに Ingress が無いので、ホストからはここだけが見える。 */
const GATEWAY_PORT = 8080;
const FRONTEND_PORT = 3000;
const FORWARDS = [
  // フロントは自分の nginx で /api を Gateway に中継するので、画面を触るだけなら
  // ここ 1 つで足りる。5173 を避けているのは、ローカルの Vite 開発サーバと
  // 取り違えないためである。
  { label: 'フロントエンド        ', service: 'frontend', port: FRONTEND_PORT, target: 8080 },
  { label: 'API Gateway        ', service: 'gatewayms', port: GATEWAY_PORT },
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

/** kind クラスタ名と名前空間。手順書と揃える。 */
const KIND_CLUSTER = 'cargo-tracker';
const NAMESPACE = 'cargo-tracker';
const OVERLAY = 'ops/k8s/overlays/local';

export default function (gulp) {
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
    const total = services.length + 1;
    services.forEach((name, i) => {
      console.log(`[${i + 1}/${total}] cargo-tracker/${name}:latest`);
      sh(`docker build -t cargo-tracker/${name}:latest -f ${name}/Dockerfile .`,
        { cwd: backend, stdio: 'inherit' });
    });
    // フロントはビルドコンテキストが自分のディレクトリなので、まとめて回さない。
    console.log(`[${total}/${total}] cargo-tracker/${FRONTEND.name}:latest`);
    sh(`docker build -t cargo-tracker/${FRONTEND.name}:latest .`,
      { cwd: `${COMPOSE_DIR}/${FRONTEND.dir}`, stdio: 'inherit' });
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
    const deployments = [...Object.keys(SERVICES), FRONTEND.name];
    for (const name of deployments) {
      sh(`kind load docker-image cargo-tracker/${name}:latest --name ${KIND_CLUSTER}`);
      sh(`kubectl --context kind-${KIND_CLUSTER} -n ${NAMESPACE} rollout restart deployment/${name}`);
    }
    console.log(`${deployments.length} 個のイメージを載せ直しました`);
    done();
  });

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
}
