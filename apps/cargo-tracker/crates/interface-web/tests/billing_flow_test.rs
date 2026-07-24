//! IT7: 輸送料金算出・法人割引の HTTP フロー統合テスト（US21/US22）。

use axum::Router;
use axum::body::Body;
use axum::http::{StatusCode, header};
use http_body_util::BodyExt;
use infra_persistence::{
    MIGRATOR, SqlxCargoRepository, SqlxCargoSpecProvider, SqlxEstimateRepository,
    SqlxFreightChargeRepository, SqlxHandlingActivityRepository, SqlxInvoiceRepository,
    SqlxNotificationRepository, SqlxSelectedRouteRepository, SqlxSelectedRouteView,
    SqlxShipperExistenceChecker, SqlxShipperRepository, SqlxTrackingActivityRepository,
    SqlxUserRepository, SqlxVoyageRepository,
};
use interface_web::{AppState, web_router};
use shared_kernel::Role;
use sqlx::PgPool;
use std::sync::Arc;
use testcontainers::ContainerAsync;
use testcontainers::ImageExt;
use testcontainers_modules::postgres::Postgres;
use testcontainers_modules::testcontainers::runners::AsyncRunner;
use tower::ServiceExt;
use tower_sessions::{MemoryStore, SessionManagerLayer};

fn app_state(pool: PgPool) -> AppState {
    AppState {
        shipper_repo: Arc::new(SqlxShipperRepository::new(pool.clone())),
        cargo_repo: Arc::new(SqlxCargoRepository::new(pool.clone())),
        shipper_checker: Arc::new(SqlxShipperExistenceChecker::new(pool.clone())),
        voyage_repo: Arc::new(SqlxVoyageRepository::new(pool.clone())),
        cargo_spec_provider: Arc::new(SqlxCargoSpecProvider::new(pool.clone())),
        selected_route_repo: Arc::new(SqlxSelectedRouteRepository::new(pool.clone())),
        notification_port: Arc::new(SqlxNotificationRepository::new(pool.clone())),
        selected_route_view: Arc::new(SqlxSelectedRouteView::new(pool.clone())),
        tracking_repo: Arc::new(SqlxTrackingActivityRepository::new(pool.clone())),
        handling_repo: Arc::new(SqlxHandlingActivityRepository::new(pool.clone())),
        estimate_repo: Arc::new(SqlxEstimateRepository::new(pool.clone())),
        charge_repo: Arc::new(SqlxFreightChargeRepository::new(pool.clone())),
        invoice_repo: Arc::new(SqlxInvoiceRepository::new(pool.clone())),
        pool,
    }
}

async fn setup() -> (Router, PgPool, ContainerAsync<Postgres>) {
    let container = Postgres::default()
        .with_tag("16-alpine")
        .start()
        .await
        .expect("start postgres");
    let port = container.get_host_port_ipv4(5432).await.expect("host port");
    let url = format!("postgres://postgres:postgres@127.0.0.1:{port}/postgres");
    let pool = PgPool::connect(&url).await.expect("connect");
    MIGRATOR.run(&pool).await.expect("migrate");

    let users = SqlxUserRepository::new(pool.clone());
    users
        .create_user("billing", "b@example.com", "pass1234", &[Role::Billing])
        .await
        .expect("seed billing");
    users
        .create_user("sales", "s@example.com", "pass1234", &[Role::Sales])
        .await
        .expect("seed sales");

    let app =
        web_router(app_state(pool.clone())).layer(SessionManagerLayer::new(MemoryStore::default()));
    (app, pool, container)
}

async fn login_as(app: &Router, username: &str) -> String {
    let resp = app
        .clone()
        .oneshot(
            axum::http::Request::post("/login")
                .header(header::CONTENT_TYPE, "application/x-www-form-urlencoded")
                .body(Body::from(format!("username={username}&password=pass1234")))
                .unwrap(),
        )
        .await
        .unwrap();
    resp.headers()
        .get(header::SET_COOKIE)
        .unwrap()
        .to_str()
        .unwrap()
        .split(';')
        .next()
        .unwrap()
        .to_string()
}

async fn post_form(app: &Router, path: &str, cookie: &str, body: &str) -> axum::response::Response {
    app.clone()
        .oneshot(
            axum::http::Request::post(path)
                .header(header::CONTENT_TYPE, "application/x-www-form-urlencoded")
                .header(header::COOKIE, cookie)
                .body(Body::from(body.to_string()))
                .unwrap(),
        )
        .await
        .unwrap()
}

async fn get(app: &Router, path: &str, cookie: &str) -> axum::response::Response {
    app.clone()
        .oneshot(
            axum::http::Request::get(path)
                .header(header::COOKIE, cookie)
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap()
}

async fn body_string(resp: axum::response::Response) -> String {
    let bytes = resp.into_body().collect().await.unwrap().to_bytes();
    String::from_utf8(bytes.to_vec()).unwrap()
}

async fn notification_count(pool: &PgPool, notification_type: &str) -> i64 {
    sqlx::query_scalar("SELECT COUNT(*) FROM notification WHERE notification_type = $1")
        .bind(notification_type)
        .fetch_one(pool)
        .await
        .unwrap()
}

/// 引取済（DELIVERED）予約と荷主を seed する。`corporate` 時は割引率 10% の法人荷主。
async fn seed_delivered_cargo(pool: &PgPool, booking_id: &str, shipper_id: &str, corporate: bool) {
    if corporate {
        sqlx::query(
            r"INSERT INTO shipper (id, shipper_code, shipper_type, name, email,
                                   contract_number, discount_rate)
              VALUES ($1::uuid, 'SHP-CORP0001', 'CORPORATE', '法人荷主', 'corp@example.com',
                      'CTR-001', 0.1000)
              ON CONFLICT (id) DO NOTHING",
        )
        .bind(shipper_id)
        .execute(pool)
        .await
        .expect("seed corporate shipper");
    } else {
        sqlx::query(
            r"INSERT INTO shipper (id, shipper_code, shipper_type, name, email)
              VALUES ($1::uuid, 'SHP-INDV0001', 'INDIVIDUAL', '個人荷主', 'indv@example.com')
              ON CONFLICT (id) DO NOTHING",
        )
        .bind(shipper_id)
        .execute(pool)
        .await
        .expect("seed individual shipper");
    }
    sqlx::query(
        r"INSERT INTO cargo
            (booking_id, shipper_id, cargo_type, weight, origin_unlocode, destination_unlocode,
             arrival_deadline, consignee_name, consignee_email, booking_status)
          VALUES ($1, $2::uuid, 'GENERAL', 1000.000, 'JPOSA', 'USLAX',
                  DATE '2026-05-20', 'Consignee', 'consignee@example.com', 'DELIVERED')",
    )
    .bind(booking_id)
    .bind(shipper_id)
    .execute(pool)
    .await
    .expect("seed cargo");
}

#[tokio::test]
async fn us21_引取済予約の料金を算出し確定できる() {
    let (app, pool, _c) = setup().await;
    seed_delivered_cargo(
        &pool,
        "BKG-B1",
        "11111111-1111-1111-1111-111111111111",
        false,
    )
    .await;
    let cookie = login_as(&app, "billing").await;

    // 料金算出 → 基本料金 (1000×100 + 5000×20)×1.0 = 200,000。個人荷主は割引なし。
    let resp = post_form(&app, "/charges", &cookie, "booking_id=BKG-B1").await;
    assert_eq!(resp.status(), StatusCode::SEE_OTHER);
    let location = resp
        .headers()
        .get(header::LOCATION)
        .unwrap()
        .to_str()
        .unwrap()
        .to_string();
    assert!(location.starts_with("/charges/FRC-"));

    let (total, status): (rust_decimal::Decimal, String) = sqlx::query_as(
        "SELECT total_amount_value, status FROM freight_charge WHERE booking_id = $1",
    )
    .bind("BKG-B1")
    .fetch_one(&pool)
    .await
    .unwrap();
    assert_eq!(total, rust_decimal::Decimal::from(200_000));
    assert_eq!(status, "DRAFT");

    // 詳細を確認 → 確定。
    let charge_id = location.trim_start_matches("/charges/").to_string();
    let show = body_string(get(&app, &location, &cookie).await).await;
    assert!(show.contains("200000"));

    let confirm = post_form(&app, &format!("/charges/{charge_id}/confirm"), &cookie, "").await;
    assert_eq!(confirm.status(), StatusCode::SEE_OTHER);
    let status2: String =
        sqlx::query_scalar("SELECT status FROM freight_charge WHERE booking_id = $1")
            .bind("BKG-B1")
            .fetch_one(&pool)
            .await
            .unwrap();
    assert_eq!(status2, "CONFIRMED");
}

#[tokio::test]
async fn us22_法人荷主は割引が適用され個人荷主は適用されない() {
    let (app, pool, _c) = setup().await;
    seed_delivered_cargo(
        &pool,
        "BKG-CORP",
        "22222222-2222-2222-2222-222222222222",
        true,
    )
    .await;
    let cookie = login_as(&app, "billing").await;

    let resp = post_form(&app, "/charges", &cookie, "booking_id=BKG-CORP").await;
    assert_eq!(resp.status(), StatusCode::SEE_OTHER);

    // 法人割引 10%: 基本 200,000 − 割引 20,000 = 180,000。
    let (base, discount, total): (
        rust_decimal::Decimal,
        Option<rust_decimal::Decimal>,
        rust_decimal::Decimal,
    ) = sqlx::query_as(
        "SELECT base_amount_value, discount_amount_value, total_amount_value \
         FROM freight_charge WHERE booking_id = $1",
    )
    .bind("BKG-CORP")
    .fetch_one(&pool)
    .await
    .unwrap();
    assert_eq!(base, rust_decimal::Decimal::from(200_000));
    assert_eq!(discount, Some(rust_decimal::Decimal::from(20_000)));
    assert_eq!(total, rust_decimal::Decimal::from(180_000));

    // 詳細画面に割引率が表示される（US22・法人時のみ）。
    let location = resp
        .headers()
        .get(header::LOCATION)
        .unwrap()
        .to_str()
        .unwrap()
        .to_string();
    let show = body_string(get(&app, &location, &cookie).await).await;
    assert!(show.contains("charge-discount"));
}

#[tokio::test]
async fn us21_引取済でない予約は料金算出できない() {
    let (app, pool, _c) = setup().await;
    sqlx::query(
        r"INSERT INTO shipper (id, shipper_code, shipper_type, name, email)
          VALUES ('33333333-3333-3333-3333-333333333333'::uuid, 'SHP-INDV0002', 'INDIVIDUAL',
                  '個人荷主2', 'indv2@example.com')",
    )
    .execute(&pool)
    .await
    .unwrap();
    sqlx::query(
        r"INSERT INTO cargo
            (booking_id, shipper_id, cargo_type, weight, origin_unlocode, destination_unlocode,
             arrival_deadline, consignee_name, consignee_email, booking_status)
          VALUES ('BKG-INTRANSIT', '33333333-3333-3333-3333-333333333333'::uuid, 'GENERAL',
                  1000.000, 'JPOSA', 'USLAX', DATE '2026-05-20', 'C', 'c@example.com', 'IN_TRANSIT')",
    )
    .execute(&pool)
    .await
    .unwrap();
    let cookie = login_as(&app, "billing").await;

    // 引取済でないため料金算出フォームにエラー表示（200 で再描画）。
    let resp = post_form(&app, "/charges", &cookie, "booking_id=BKG-INTRANSIT").await;
    assert_eq!(resp.status(), StatusCode::OK);
    let body = body_string(resp).await;
    assert!(body.contains("引取済"));

    let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM freight_charge")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(count, 0);
}

#[tokio::test]
async fn 料金算出は経理ロール以外は禁止される() {
    let (app, pool, _c) = setup().await;
    seed_delivered_cargo(
        &pool,
        "BKG-B9",
        "44444444-4444-4444-4444-444444444444",
        false,
    )
    .await;
    // sales ロールでは料金一覧にアクセスできない（403）。
    let cookie = login_as(&app, "sales").await;
    let resp = get(&app, "/charges", &cookie).await;
    assert_eq!(resp.status(), StatusCode::FORBIDDEN);
}

#[tokio::test]
async fn us21_例外時の料金調整を入力すると合計から控除される() {
    let (app, pool, _c) = setup().await;
    seed_delivered_cargo(
        &pool,
        "BKG-ADJ",
        "55555555-5555-5555-5555-555555555555",
        false,
    )
    .await;
    let cookie = login_as(&app, "billing").await;

    // 遅延減額 15,000 を入力 → 基本 200,000 − 15,000 = 185,000。
    let body = "booking_id=BKG-ADJ&adjustment_reason=DELAY_REDUCTION&adjustment_amount=15000";
    let resp = post_form(&app, "/charges", &cookie, body).await;
    assert_eq!(resp.status(), StatusCode::SEE_OTHER);

    let total: rust_decimal::Decimal =
        sqlx::query_scalar("SELECT total_amount_value FROM freight_charge WHERE booking_id = $1")
            .bind("BKG-ADJ")
            .fetch_one(&pool)
            .await
            .unwrap();
    assert_eq!(total, rust_decimal::Decimal::from(185_000));

    // 調整が永続化されている。
    let adj_count: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM freight_charge_adjustment a \
         JOIN freight_charge f ON f.id = a.freight_charge_id WHERE f.booking_id = $1",
    )
    .bind("BKG-ADJ")
    .fetch_one(&pool)
    .await
    .unwrap();
    assert_eq!(adj_count, 1);
}

#[tokio::test]
async fn us21_再算出は同じ料金idで冪等に更新され二重登録しない() {
    let (app, pool, _c) = setup().await;
    seed_delivered_cargo(
        &pool,
        "BKG-RECALC",
        "66666666-6666-6666-6666-666666666666",
        false,
    )
    .await;
    let cookie = login_as(&app, "billing").await;

    // 1 回目の算出。
    let resp1 = post_form(&app, "/charges", &cookie, "booking_id=BKG-RECALC").await;
    let loc1 = resp1
        .headers()
        .get(header::LOCATION)
        .unwrap()
        .to_str()
        .unwrap()
        .to_string();
    // 2 回目の算出（再算出）→ 同じ charge_id にリダイレクトされ、詳細が 200 で開ける。
    let resp2 = post_form(&app, "/charges", &cookie, "booking_id=BKG-RECALC").await;
    let loc2 = resp2
        .headers()
        .get(header::LOCATION)
        .unwrap()
        .to_str()
        .unwrap()
        .to_string();
    assert_eq!(loc1, loc2);
    let show = get(&app, &loc2, &cookie).await;
    assert_eq!(show.status(), StatusCode::OK);

    // 料金は 1 件のみ（二重登録なし・冪等）。
    let count: i64 =
        sqlx::query_scalar("SELECT COUNT(*) FROM freight_charge WHERE booking_id = $1")
            .bind("BKG-RECALC")
            .fetch_one(&pool)
            .await
            .unwrap();
    assert_eq!(count, 1);
}

/// 確定料金を用意する（料金算出→確定）。charge_id を返す。
async fn confirm_charge(app: &Router, cookie: &str, booking_id: &str) -> String {
    let resp = post_form(app, "/charges", cookie, &format!("booking_id={booking_id}")).await;
    let loc = resp
        .headers()
        .get(header::LOCATION)
        .unwrap()
        .to_str()
        .unwrap()
        .to_string();
    let charge_id = loc.trim_start_matches("/charges/").to_string();
    post_form(app, &format!("/charges/{charge_id}/confirm"), cookie, "").await;
    charge_id
}

#[tokio::test]
async fn us23_精算書を発行し入金確認で精算完了し予約が精算済になる() {
    let (app, pool, _c) = setup().await;
    seed_delivered_cargo(
        &pool,
        "BKG-INV1",
        "77777777-7777-7777-7777-777777777777",
        false,
    )
    .await;
    let cookie = login_as(&app, "billing").await;
    let charge_id = confirm_charge(&app, &cookie, "BKG-INV1").await;

    // 精算書を発行 → 請求金額 = 200,000 + 消費税 20,000 = 220,000・荷主へ通知。
    let resp = post_form(
        &app,
        "/billing/invoices",
        &cookie,
        &format!("charge_id={charge_id}"),
    )
    .await;
    assert_eq!(resp.status(), StatusCode::SEE_OTHER);
    let loc = resp
        .headers()
        .get(header::LOCATION)
        .unwrap()
        .to_str()
        .unwrap()
        .to_string();
    assert!(loc.starts_with("/billing/invoices/INV-"));

    let (total, status): (rust_decimal::Decimal, String) = sqlx::query_as(
        "SELECT total_amount_value, payment_status FROM invoice WHERE booking_id = $1",
    )
    .bind("BKG-INV1")
    .fetch_one(&pool)
    .await
    .unwrap();
    assert_eq!(total, rust_decimal::Decimal::from(220_000));
    assert_eq!(status, "PENDING");
    // 荷主へ精算書発行通知（宛先＝荷受人）。
    assert_eq!(notification_count(&pool, "INVOICE_ISSUED").await, 1);

    // 入金確認 → 精算済・予約も精算済（Settled）。cargo を DELIVERED に更新済み前提。
    let inv_number = loc.trim_start_matches("/billing/invoices/").to_string();
    let confirm = post_form(
        &app,
        &format!("/billing/invoices/{inv_number}/confirm-payment"),
        &cookie,
        "",
    )
    .await;
    assert_eq!(confirm.status(), StatusCode::SEE_OTHER);

    let pay_status: String =
        sqlx::query_scalar("SELECT payment_status FROM invoice WHERE booking_id = $1")
            .bind("BKG-INV1")
            .fetch_one(&pool)
            .await
            .unwrap();
    assert_eq!(pay_status, "CONFIRMED");
    let booking_status: String =
        sqlx::query_scalar("SELECT booking_status FROM cargo WHERE booking_id = $1")
            .bind("BKG-INV1")
            .fetch_one(&pool)
            .await
            .unwrap();
    assert_eq!(booking_status, "SETTLED");
    // 支払記録が残る。
    let pay_count: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM payment p JOIN invoice i ON i.id = p.invoice_id WHERE i.booking_id = $1",
    )
    .bind("BKG-INV1")
    .fetch_one(&pool)
    .await
    .unwrap();
    assert_eq!(pay_count, 1);
}

#[tokio::test]
async fn us23_未確定料金からは精算書を発行できない() {
    let (app, pool, _c) = setup().await;
    seed_delivered_cargo(
        &pool,
        "BKG-INV2",
        "88888888-8888-8888-8888-888888888888",
        false,
    )
    .await;
    let cookie = login_as(&app, "billing").await;
    // 料金を算出するが確定しない（Draft のまま）。
    let resp = post_form(&app, "/charges", &cookie, "booking_id=BKG-INV2").await;
    let charge_id = resp
        .headers()
        .get(header::LOCATION)
        .unwrap()
        .to_str()
        .unwrap()
        .trim_start_matches("/charges/")
        .to_string();

    let issue = post_form(
        &app,
        "/billing/invoices",
        &cookie,
        &format!("charge_id={charge_id}"),
    )
    .await;
    assert_eq!(issue.status(), StatusCode::UNPROCESSABLE_ENTITY);
    let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM invoice")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(count, 0);
}
