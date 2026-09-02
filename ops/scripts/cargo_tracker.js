'use strict';

/**
 * cargo-tracker の運用タスク。
 *
 * 手順書: docs/design/cargo-tracker/operation.md
 *
 * ここに置くのは「運用手順書に載っている操作」だけ。調査用の使い捨てはスクラッチパッドに置く。
 */

import { execSync } from 'child_process';

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

export default function (gulp) {
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
