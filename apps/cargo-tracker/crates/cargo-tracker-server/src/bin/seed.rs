//! 開発用シードデータの seed バイナリ。
//!
//! ログイン検証用のロール別ユーザーに加え、業務フロー（荷主登録 → 貨物予約 →
//! 航海スケジュール登録 → 経路確定）に沿ったマスタ・トランザクションデータを
//! 冪等に投入する（既存データはスキップ）。パスワードは argon2 でハッシュ化して
//! 保存する（`SqlxUserRepository` に委譲）。
//!
//! 実行例:
//! ```bash
//! DATABASE_URL=postgres://cargo:cargo@127.0.0.1:5432/cargo_tracker \
//!   cargo run -p cargo-tracker-server --bin seed
//! ```
//!
//! 開発専用。本番環境では実行しないこと。

use infra_persistence::{MIGRATOR, SqlxUserRepository};
use shared_kernel::Role;
use sqlx::{PgPool, Row};

/// 投入する開発用ユーザー（username, email, password, roles）。
const DEV_USERS: &[(&str, &str, &str, &[Role])] = &[
    ("sales", "sales@example.com", "password", &[Role::Sales]),
    (
        "shipper",
        "shipper@example.com",
        "password",
        &[Role::Shipper],
    ),
    (
        "consignee",
        "consignee@example.com",
        "password",
        &[Role::Consignee],
    ),
    (
        "tracker",
        "tracker@example.com",
        "password",
        &[Role::Tracker],
    ),
    (
        "handler",
        "handler@example.com",
        "password",
        &[Role::Handler],
    ),
    (
        "designer",
        "designer@example.com",
        "password",
        &[Role::RouteDesigner],
    ),
    (
        "billing",
        "billing@example.com",
        "password",
        &[Role::Billing],
    ),
    ("admin", "admin@example.com", "password", &[Role::Admin]),
];

fn database_url() -> String {
    std::env::var("DATABASE_URL")
        .unwrap_or_else(|_| "postgres://cargo:cargo@127.0.0.1:5432/cargo_tracker".to_string())
}

#[tokio::main]
async fn main() {
    let pool = PgPool::connect(&database_url())
        .await
        .expect("データベースに接続できません");
    MIGRATOR
        .run(&pool)
        .await
        .expect("マイグレーションの適用に失敗しました");

    let repo = SqlxUserRepository::new(pool.clone());
    let mut created = 0;
    let mut skipped = 0;

    for (username, email, password, roles) in DEV_USERS {
        let exists = repo
            .find_credentials(username)
            .await
            .expect("ユーザー検索に失敗しました")
            .is_some();
        if exists {
            println!("スキップ: {username}（既に存在）");
            skipped += 1;
            continue;
        }
        repo.create_user(username, email, password, roles)
            .await
            .expect("ユーザー作成に失敗しました");
        let role_str: Vec<&str> = roles.iter().map(|r| r.as_str()).collect();
        println!("作成: {username} / password（{}）", role_str.join(", "));
        created += 1;
    }

    println!("ユーザー seed 完了: 作成 {created} 件 / スキップ {skipped} 件");

    seed_business_flow(&pool)
        .await
        .expect("業務フローデータの投入に失敗しました");

    println!("ログイン例: ユーザー名 sales / パスワード password");
}

/// 業務フロー（荷主 → 予約 → 航海 → 確定経路）に沿ったデータを冪等に投入する。
///
/// 冪等性は「代表レコード（貨物 `BKG-0001`）の存在」で判定し、既に投入済みなら
/// 何もしない。マスタとなる港（`location`）だけは共有カーネルとして常に UPSERT する。
async fn seed_business_flow(pool: &PgPool) -> Result<(), sqlx::Error> {
    // 港マスタ（Shared Kernel）: 航路が参照する主要港。常に冪等 UPSERT。
    sqlx::query(
        r"INSERT INTO location (unlocode, name, country_code, time_zone) VALUES
            ('JPOSA', '大阪',          'JP', 'Asia/Tokyo'),
            ('JPTYO', '東京',          'JP', 'Asia/Tokyo'),
            ('CNSHA', '上海',          'CN', 'Asia/Shanghai'),
            ('SGSIN', 'シンガポール',   'SG', 'Asia/Singapore'),
            ('USLAX', 'ロサンゼルス',   'US', 'America/Los_Angeles'),
            ('USNYC', 'ニューヨーク',   'US', 'America/New_York'),
            ('DEHAM', 'ハンブルク',     'DE', 'Europe/Berlin'),
            ('NLRTM', 'ロッテルダム',   'NL', 'Europe/Amsterdam')
          ON CONFLICT (unlocode) DO NOTHING",
    )
    .execute(pool)
    .await?;

    let already = sqlx::query("SELECT EXISTS (SELECT 1 FROM cargo WHERE booking_id = 'BKG-0001')")
        .fetch_one(pool)
        .await?
        .get::<bool, _>(0);
    if already {
        println!("業務フロー seed: スキップ（BKG-0001 が既に存在）");
        return Ok(());
    }

    // トランザクションで一括投入（途中失敗時はロールバック）。
    let mut tx = pool.begin().await?;
    sqlx::raw_sql(BUSINESS_FLOW_SQL).execute(&mut *tx).await?;
    tx.commit().await?;

    println!(
        "業務フロー seed 完了: 荷主 2 / 予約 5（BKG-0001 仮受付・BKG-0002 経路提案中・BKG-0003 仮受付冷凍・BKG-0004 経路設計中/期限内候補あり・BKG-0005 経路設計中/期限超過のみ）/ 航海 4 / 確定経路 1"
    );
    println!("デモ手順:");
    println!("  US06 経路設計依頼: sales で BKG-0001 →[経路設計を依頼する]（仮受付→経路設計中）");
    println!(
        "  US10/US11 条件調整→確定紐付け: designer で BKG-0005 →⚠期限超過を期限延長で再算出→確定 / BKG-0004 →候補を確定（経路設計中→経路提案中）"
    );
    println!(
        "  US12/US13 荷主通知→確定/差戻し/キャンセル: sales で BKG-0002 →[荷主に経路を通知する]→[予約を確定する]"
    );
    println!(
        "  US14 追跡番号発行: designer で BKG-0006（確定済）→[追跡番号を発行する]（確定→追跡番号発行済）"
    );
    println!(
        "  US15/US16 荷役・引取: handler で 荷役管理→新規登録→追跡番号 TRK-DEMO-0007 で受領/積込/荷降し/引取記録"
    );
    println!("  US17 手動更新: tracker で 貨物追跡→TRK-DEMO-0007→[手動更新]（出港=搭載中 等）");
    Ok(())
}

/// 業務フローデータ本体。荷主 → 予約 → 航海 → 確定経路の順に依存関係を満たす。
///
/// - 荷主: 個人（田中太郎）・法人（山田物流）
/// - 航海: V0001 直行（大阪→LA）/ V0002 大阪→シンガポール / V0003 シンガポール→ハンブルク
///   （V0002+V0003 で 2 区間接続）/ V0004 危険物対応の代替直行（大阪→LA）
/// - 予約: BKG-0001 直行候補あり（仮受付・US06 デモ）/ BKG-0002 2 区間経路を確定済（経路提案中・US12/US13 デモ）/
///   BKG-0003 冷凍（仮受付）/ BKG-0004 経路設計中・期限内直行便あり（US11 確定紐付けデモ）/
///   BKG-0005 経路設計中・期限超過のみ（US10 条件調整デモ）
/// - 確定経路: BKG-0002 に V0002→V0003 の 2 区間を割り当て
/// - IT4 デモ項目（予約状態機械）を一通り実演できる状態を用意する
const BUSINESS_FLOW_SQL: &str = r"
-- 荷主（Shipper Context）------------------------------------------------------
INSERT INTO shipper (id, shipper_code, shipper_type, name, email, phone, address, contract_number, discount_rate) VALUES
    ('11111111-1111-1111-1111-111111111111', 'SHP-00000001', 'INDIVIDUAL', '田中 太郎',
     'tanaka@example.com', '06-1234-5678', '大阪府大阪市北区梅田1-1-1', NULL, 0.0000),
    ('22222222-2222-2222-2222-222222222222', 'SHP-00000002', 'CORPORATE', '山田物流株式会社',
     'sales@yamada-logistics.example.com', '03-9876-5432', '東京都港区海岸2-2-2', 'CTR-2026-0007', 0.1500);

-- 貨物予約（Booking Context）--------------------------------------------------
INSERT INTO cargo
    (booking_id, shipper_id, cargo_type, weight, origin_unlocode, destination_unlocode,
     arrival_deadline, consignee_name, consignee_email, booking_status,
     min_temperature, max_temperature, temperature_unit) VALUES
    ('BKG-0001', '11111111-1111-1111-1111-111111111111', 'GENERAL', 1200.000,
     'JPOSA', 'USLAX', DATE '2026-05-20', 'LA Trading Inc.', 'consignee@la-trading.example.com',
     'PRELIMINARY', NULL, NULL, NULL),
    ('BKG-0002', '22222222-2222-2222-2222-222222222222', 'GENERAL', 8500.000,
     'JPOSA', 'DEHAM', DATE '2026-06-05', 'Hamburg Handels GmbH', 'import@hamburg-handels.example.com',
     'ROUTE_PROPOSED', NULL, NULL, NULL),
    ('BKG-0003', '22222222-2222-2222-2222-222222222222', 'REFRIGERATED', 3000.000,
     'JPOSA', 'SGSIN', DATE '2026-05-15', 'SG Cold Chain Pte', 'ops@sg-coldchain.example.com',
     'PRELIMINARY', -18.000, -5.000, 'CELSIUS'),
    -- BKG-0004: 経路設計中・期限内に直行便あり（demo2 の確定紐付け用。V0001 到着 05-14 ≤ 期限 05-20）
    ('BKG-0004', '11111111-1111-1111-1111-111111111111', 'GENERAL', 1500.000,
     'JPOSA', 'USLAX', DATE '2026-05-20', 'LA Trading Inc.', 'consignee@la-trading.example.com',
     'ROUTE_DESIGNING', NULL, NULL, NULL),
    -- BKG-0005: 経路設計中だが期限内経路が 0 件（demo2 の条件調整用。V0001 到着 05-14 > 期限 05-10 で ⚠ 期限超過）
    ('BKG-0005', '22222222-2222-2222-2222-222222222222', 'GENERAL', 2000.000,
     'JPOSA', 'USLAX', DATE '2026-05-10', 'LA Trading Inc.', 'consignee@la-trading.example.com',
     'ROUTE_DESIGNING', NULL, NULL, NULL),
    -- BKG-0006: 予約確定（IT5 US14 追跡番号発行デモ用）
    ('BKG-0006', '11111111-1111-1111-1111-111111111111', 'GENERAL', 1800.000,
     'JPOSA', 'USLAX', DATE '2026-05-20', 'LA Trading Inc.', 'consignee@la-trading.example.com',
     'CONFIRMED', NULL, NULL, NULL),
    -- BKG-0007: 追跡番号発行済（IT5 US15/US16/US17 荷役・手動更新デモ用）
    ('BKG-0007', '11111111-1111-1111-1111-111111111111', 'GENERAL', 1600.000,
     'JPOSA', 'USLAX', DATE '2026-05-20', 'LA Trading Inc.', 'consignee@la-trading.example.com',
     'TRACKING_ISSUED', NULL, NULL, NULL);

-- 追跡活動（Tracking Context・BKG-0007 は発行済み＝受領待ち。IT5 US15/16/17 デモ用）--
INSERT INTO tracking_activity (tracking_number, booking_id, transport_status) VALUES
    ('TRK-DEMO-0007', 'BKG-0007', 'NOT_RECEIVED');

-- 航海スケジュール（Routing Context）------------------------------------------
INSERT INTO voyage (voyage_number, vessel_name, carrier) VALUES
    ('V0001', 'SAKURA MARU',   'Nippon Line'),
    ('V0002', 'ASIA STAR',     'Orient Shipping'),
    ('V0003', 'EURO BRIDGE',   'Orient Shipping'),
    ('V0004', 'HAZMAT CARRIER','Pacific Freight');

INSERT INTO carrier_movement
    (voyage_id, departure_location_unlocode, arrival_location_unlocode, departure_date, arrival_date, seq_number) VALUES
    ((SELECT id FROM voyage WHERE voyage_number = 'V0001'), 'JPOSA', 'USLAX', TIMESTAMPTZ '2026-05-01 18:00:00+09', TIMESTAMPTZ '2026-05-14 08:00:00-07', 1),
    ((SELECT id FROM voyage WHERE voyage_number = 'V0002'), 'JPOSA', 'SGSIN', TIMESTAMPTZ '2026-05-02 09:00:00+09', TIMESTAMPTZ '2026-05-09 18:00:00+08', 1),
    ((SELECT id FROM voyage WHERE voyage_number = 'V0003'), 'SGSIN', 'DEHAM', TIMESTAMPTZ '2026-05-11 10:00:00+08', TIMESTAMPTZ '2026-05-30 16:00:00+02', 1),
    ((SELECT id FROM voyage WHERE voyage_number = 'V0004'), 'JPOSA', 'USLAX', TIMESTAMPTZ '2026-05-05 20:00:00+09', TIMESTAMPTZ '2026-05-18 09:00:00-07', 1);

INSERT INTO voyage_cargo_type (voyage_id, cargo_type) VALUES
    ((SELECT id FROM voyage WHERE voyage_number = 'V0001'), 'GENERAL'),
    ((SELECT id FROM voyage WHERE voyage_number = 'V0002'), 'GENERAL'),
    ((SELECT id FROM voyage WHERE voyage_number = 'V0002'), 'REFRIGERATED'),
    ((SELECT id FROM voyage WHERE voyage_number = 'V0003'), 'GENERAL'),
    ((SELECT id FROM voyage WHERE voyage_number = 'V0004'), 'HAZARDOUS'),
    ((SELECT id FROM voyage WHERE voyage_number = 'V0004'), 'GENERAL');

-- 確定経路（BKG-0002 に V0002→V0003 の 2 区間を割り当て）-----------------------
INSERT INTO selected_route (booking_id, status) VALUES ('BKG-0002', 'SELECTED');

INSERT INTO selected_route_leg
    (selected_route_id, voyage_number, load_location_unlocode, unload_location_unlocode, load_time, unload_time, seq_number) VALUES
    ((SELECT id FROM selected_route WHERE booking_id = 'BKG-0002'), 'V0002', 'JPOSA', 'SGSIN', TIMESTAMPTZ '2026-05-02 09:00:00+09', TIMESTAMPTZ '2026-05-09 18:00:00+08', 1),
    ((SELECT id FROM selected_route WHERE booking_id = 'BKG-0002'), 'V0003', 'SGSIN', 'DEHAM', TIMESTAMPTZ '2026-05-11 10:00:00+08', TIMESTAMPTZ '2026-05-30 16:00:00+02', 2);
";
