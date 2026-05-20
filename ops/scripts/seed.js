'use strict';

/**
 * Docker 環境（local-docker プロファイル）向けのサンプルシードデータ投入スクリプト。
 *
 * 提供タスク:
 *   - seed:docker : http://localhost:8080 経由で荷主 5 件・予約 5 件を投入する
 *
 * 前提:
 *   - `local-docker:up` で全サービス（gatewayms 8080 / authms 8081 / bookingms 8082 ...）が
 *     起動済みであること
 *   - 認証ユーザー `admin / password` が authms に存在すること（local-docker プロファイルの
 *     デフォルトシード）
 *
 * 設計方針:
 *   - 外部依存ゼロ（Node.js 18+ 標準の global fetch を使用）
 *   - 部分失敗を許容（1 件失敗しても全体を止めず、最後にサマリーを表示）
 *   - 既存データとの重複は警告のみ（409/4xx でもログ出力して継続）
 *   - 全ログに `[seed]` プレフィックスを付与
 */

const BASE = process.env.SEED_API_BASE || 'http://localhost:8080';

/** コンソール出力ヘルパ（[seed] プレフィックス付き）。 */
function log(...args) {
  console.log('[seed]', ...args);
}
function warn(...args) {
  console.warn('[seed]', ...args);
}
function error(...args) {
  console.error('[seed]', ...args);
}

/**
 * JSON POST のラッパ。
 * - 失敗しても throw せず { ok, status, data } を返す（呼び出し側で重複/異常を判定する）
 * - ネットワークエラー時は ok=false, status=0, error メッセージを返す
 */
async function apiPost(url, body, token) {
  const headers = { 'Content-Type': 'application/json', Accept: 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  try {
    const res = await fetch(url, {
      method: 'POST',
      headers,
      body: JSON.stringify(body),
    });
    const data = await res.json().catch(() => null);
    return { ok: res.ok, status: res.status, data };
  } catch (err) {
    return { ok: false, status: 0, data: null, error: err.message };
  }
}

/** 認証してアクセストークンを取得する。失敗時は null。 */
async function login() {
  log(`認証中: ${BASE}/api/v1/auth/login`);
  const res = await apiPost(`${BASE}/api/v1/auth/login`, {
    username: 'admin',
    password: 'password',
  });
  if (!res.ok) {
    error(`認証失敗: status=${res.status} body=${JSON.stringify(res.data)} ${res.error || ''}`);
    return null;
  }
  // 想定するレスポンス: { accessToken, refreshToken, ... } もしくは { token } 等
  // 主要なフィールド名を順に試す。
  const token =
    res.data?.accessToken ||
    res.data?.access_token ||
    res.data?.token ||
    res.data?.data?.accessToken ||
    null;
  if (!token) {
    error(`認証レスポンスからトークンを抽出できません: ${JSON.stringify(res.data)}`);
    return null;
  }
  log('認証成功（アクセストークン取得）');
  return token;
}

/** 荷主リスト（5 件）。 */
const SHIPPERS = [
  { name: '東京海運株式会社', email: 'info@tokyo-kaiu.example.com', shipperType: 'CORPORATE' },
  { name: '大阪貿易商事', email: 'trade@osaka-boeki.example.com', shipperType: 'CORPORATE' },
  { name: '横浜インターナショナル', email: 'contact@yokohama-intl.example.com', shipperType: 'CORPORATE' },
  { name: '田中物流', email: 'tanaka@tanaka-butsuryu.example.com', shipperType: 'INDIVIDUAL' },
  { name: '鈴木商会', email: 'suzuki@suzuki-shokai.example.com', shipperType: 'INDIVIDUAL' },
];

/**
 * 予約データテンプレート（shipperId は登録後に動的に注入する）。
 * SHIPPERS の各要素と 1:1 対応する。
 */
const BOOKING_TEMPLATES = [
  {
    cargoSpec: { cargoType: 'GENERAL', weightKg: 500, quantity: 10, productName: '電子部品' },
    routeSpec: { originUnLocode: 'JPTYO', destinationUnLocode: 'DEHAM', arrivalDeadline: '2026-12-31' },
  },
  {
    cargoSpec: {
      cargoType: 'REFRIGERATED',
      weightKg: 200,
      quantity: 5,
      productName: '冷凍食品',
      temperatureCondition: { minCelsius: -20, maxCelsius: -10 },
    },
    routeSpec: { originUnLocode: 'JPOSA', destinationUnLocode: 'USNYC', arrivalDeadline: '2026-11-30' },
  },
  {
    cargoSpec: {
      cargoType: 'HAZARDOUS',
      weightKg: 100,
      quantity: 2,
      productName: '化学薬品',
      hazardInfo: { imoClass: '3', unNumber: 'UN1203', declaration: '引火性液体' },
    },
    routeSpec: { originUnLocode: 'JPYOK', destinationUnLocode: 'SGSIN', arrivalDeadline: '2026-10-31' },
  },
  {
    cargoSpec: { cargoType: 'GENERAL', weightKg: 50, quantity: 3, productName: '衣料品' },
    routeSpec: { originUnLocode: 'JPTYO', destinationUnLocode: 'GBLON', arrivalDeadline: '2026-09-30' },
  },
  {
    cargoSpec: { cargoType: 'GENERAL', weightKg: 300, quantity: 8, productName: '自動車部品' },
    routeSpec: { originUnLocode: 'JPNGO', destinationUnLocode: 'DEHAM', arrivalDeadline: '2026-08-31' },
  },
];

/**
 * 荷主を 1 件投入する。
 * - 成功: { ok: true, id }
 * - 重複（409 など 4xx）: { ok: false, duplicate: true } を返し警告のみ
 * - その他失敗: { ok: false, duplicate: false }
 */
async function createShipper(shipper, token) {
  const res = await apiPost(`${BASE}/api/v1/shippers`, shipper, token);
  if (res.ok) {
    const id = res.data?.id || res.data?.shipperId || res.data?.data?.id || null;
    if (!id) {
      warn(`荷主 [${shipper.name}] のレスポンスから ID を抽出できません: ${JSON.stringify(res.data)}`);
      return { ok: false, duplicate: false };
    }
    log(`荷主登録: [${shipper.name}] id=${id}`);
    return { ok: true, id };
  }
  // 4xx は重複の可能性が高い（既存データ）。エラー扱いせず警告のみ。
  if (res.status >= 400 && res.status < 500) {
    warn(
      `荷主 [${shipper.name}] の登録をスキップ（既存または検証エラー）: ` +
        `status=${res.status} body=${JSON.stringify(res.data)}`,
    );
    return { ok: false, duplicate: true };
  }
  error(
    `荷主 [${shipper.name}] の登録に失敗: status=${res.status} body=${JSON.stringify(res.data)} ${res.error || ''}`,
  );
  return { ok: false, duplicate: false };
}

/**
 * 予約を 1 件投入する。
 */
async function createBooking(booking, token, label) {
  const res = await apiPost(`${BASE}/api/v1/bookings`, booking, token);
  if (res.ok) {
    const id = res.data?.id || res.data?.bookingId || res.data?.data?.id || '(id 不明)';
    log(`予約登録: ${label} id=${id}`);
    return { ok: true };
  }
  if (res.status >= 400 && res.status < 500) {
    warn(
      `予約 ${label} の登録をスキップ（既存または検証エラー）: ` +
        `status=${res.status} body=${JSON.stringify(res.data)}`,
    );
    return { ok: false, duplicate: true };
  }
  error(
    `予約 ${label} の登録に失敗: status=${res.status} body=${JSON.stringify(res.data)} ${res.error || ''}`,
  );
  return { ok: false, duplicate: false };
}

/**
 * シード本体。
 * 認証 → 荷主登録 → 予約登録 の順に実行し、各ステップの結果サマリーを返す。
 */
async function runSeed() {
  log(`シード開始: BASE=${BASE}`);

  const token = await login();
  if (!token) {
    return { fatal: true, message: '認証に失敗したためシードを中断しました。' };
  }

  // === Step 2: 荷主登録 ===
  const shipperSummary = { success: 0, duplicate: 0, failed: 0 };
  const shipperIds = []; // 予約作成時に index 順で使用（duplicate/failed は null）

  for (const shipper of SHIPPERS) {
    const result = await createShipper(shipper, token);
    if (result.ok) {
      shipperSummary.success += 1;
      shipperIds.push(result.id);
    } else if (result.duplicate) {
      shipperSummary.duplicate += 1;
      shipperIds.push(null); // 重複時は ID 不明 → 予約はスキップ
    } else {
      shipperSummary.failed += 1;
      shipperIds.push(null);
    }
  }

  // === Step 3: 予約登録（荷主 ID が取れたものだけ） ===
  const bookingSummary = { success: 0, duplicate: 0, failed: 0, skipped: 0 };

  for (let i = 0; i < BOOKING_TEMPLATES.length; i += 1) {
    const shipperId = shipperIds[i];
    const shipperName = SHIPPERS[i].name;
    if (!shipperId) {
      warn(`予約 [${shipperName}] をスキップ（対応する荷主 ID が取得できませんでした）`);
      bookingSummary.skipped += 1;
      continue;
    }
    const booking = { shipperId, ...BOOKING_TEMPLATES[i] };
    const label = `[${shipperName}] ${booking.cargoSpec.productName} ${booking.routeSpec.originUnLocode}→${booking.routeSpec.destinationUnLocode}`;
    const result = await createBooking(booking, token, label);
    if (result.ok) bookingSummary.success += 1;
    else if (result.duplicate) bookingSummary.duplicate += 1;
    else bookingSummary.failed += 1;
  }

  return { fatal: false, shipperSummary, bookingSummary };
}

/** サマリーを整形して表示する。 */
function printSummary(result) {
  log('---------------- サマリー ----------------');
  if (result.fatal) {
    error(result.message);
    return;
  }
  const s = result.shipperSummary;
  const b = result.bookingSummary;
  log(`荷主: 成功=${s.success} 既存/警告=${s.duplicate} 失敗=${s.failed} (計 ${SHIPPERS.length})`);
  log(
    `予約: 成功=${b.success} 既存/警告=${b.duplicate} 失敗=${b.failed} スキップ=${b.skipped} ` +
      `(計 ${BOOKING_TEMPLATES.length})`,
  );
  log('------------------------------------------');
}

/**
 * gulp タスクを登録する。
 *
 * @param {import('gulp').Gulp} gulp
 */
export default function (gulp) {
  gulp.task('seed:docker', async (done) => {
    try {
      const result = await runSeed();
      printSummary(result);
      // 仕様：部分失敗は許容する → fatal 以外は成功扱いで done(null)
      if (result.fatal) {
        done(new Error(result.message));
        return;
      }
      done(null);
    } catch (err) {
      error(`予期しないエラー: ${err.message}`);
      done(err);
    }
  });
}
