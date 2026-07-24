//! IT5: 追跡・荷役の HTTP フロー統合テスト（US14/US15/US16/US17）。
//!
//! 実 PostgreSQL（testcontainers）＋ axum Router（oneshot）で、追跡番号発行から
//! 荷役反映・手動更新までを検証する。

use axum::Router;
use axum::body::Body;
use axum::http::{StatusCode, header};
use infra_persistence::{
    MIGRATOR, SqlxCargoRepository, SqlxCargoSpecProvider, SqlxEstimateRepository,
    SqlxFreightChargeRepository, SqlxHandlingActivityRepository, SqlxInvoiceRepository,
    SqlxNotificationRepository, SqlxSelectedRouteRepository, SqlxSelectedRouteView,
    SqlxShipperExistenceChecker, SqlxShipperRepository, SqlxTrackingActivityRepository,
    SqlxUserRepository, SqlxVoyageRepository,
};
use interface_web::{AppState, web_router};
use shared_kernel::{Role, ShipperId};
use sqlx::{PgPool, Row};
use std::sync::Arc;
use testcontainers::ContainerAsync;
use testcontainers::ImageExt;
use testcontainers_modules::postgres::Postgres;
use testcontainers_modules::testcontainers::runners::AsyncRunner;
use tower::ServiceExt;
use tower_sessions::{MemoryStore, SessionManagerLayer};

use domain_shipper::{Email, Shipper, ShipperKind, ShipperName, ShipperRepository};

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

async fn setup() -> (Router, ShipperId, PgPool, ContainerAsync<Postgres>) {
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
        .create_user(
            "designer",
            "d@example.com",
            "pass1234",
            &[Role::RouteDesigner],
        )
        .await
        .expect("seed designer");
    users
        .create_user("handler", "h@example.com", "pass1234", &[Role::Handler])
        .await
        .expect("seed handler");
    users
        .create_user("tracker", "t@example.com", "pass1234", &[Role::Tracker])
        .await
        .expect("seed tracker");

    let shipper = Shipper::register(
        ShipperName::new("山田商事").unwrap(),
        Email::new("yamada@example.com").unwrap(),
        None,
        None,
        ShipperKind::Individual,
    );
    let shipper_id = shipper.id();
    SqlxShipperRepository::new(pool.clone())
        .save(&shipper)
        .await
        .expect("seed shipper");

    let app =
        web_router(app_state(pool.clone())).layer(SessionManagerLayer::new(MemoryStore::default()));
    (app, shipper_id, pool, container)
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

async fn seed_cargo(pool: &PgPool, shipper_id: &ShipperId, booking_id: &str, status: &str) {
    sqlx::query(
        r"INSERT INTO cargo
            (booking_id, shipper_id, cargo_type, weight, origin_unlocode,
             destination_unlocode, arrival_deadline, consignee_name, consignee_email, booking_status)
          VALUES ($1, $2, 'GENERAL', 1000, 'JPOSA', 'USLAX', DATE '2026-06-05',
                  'LA Trading', 'import@la.example', $3)",
    )
    .bind(booking_id)
    .bind(shipper_id.value())
    .bind(status)
    .execute(pool)
    .await
    .expect("seed cargo");
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

async fn cargo_status(pool: &PgPool, booking_id: &str) -> String {
    sqlx::query("SELECT booking_status FROM cargo WHERE booking_id = $1")
        .bind(booking_id)
        .fetch_one(pool)
        .await
        .unwrap()
        .try_get("booking_status")
        .unwrap()
}

async fn tracking_number_for(pool: &PgPool, booking_id: &str) -> Option<(String, String)> {
    sqlx::query(
        "SELECT tracking_number, transport_status FROM tracking_activity WHERE booking_id = $1",
    )
    .bind(booking_id)
    .fetch_optional(pool)
    .await
    .unwrap()
    .map(|r| {
        (
            r.try_get::<String, _>("tracking_number").unwrap(),
            r.try_get::<String, _>("transport_status").unwrap(),
        )
    })
}

/// 指定予約・種別の通知記録件数を返す（US14/US15/US17 の「送信＝記録」検証）。
async fn notification_count(pool: &PgPool, booking_id: &str, notification_type: &str) -> i64 {
    sqlx::query(
        "SELECT COUNT(*) AS c FROM notification WHERE booking_id = $1 AND notification_type = $2",
    )
    .bind(booking_id)
    .bind(notification_type)
    .fetch_one(pool)
    .await
    .unwrap()
    .try_get::<i64, _>("c")
    .unwrap()
}

#[tokio::test]
async fn us14_確定予約に追跡番号を発行できる() {
    let (app, shipper_id, pool, _c) = setup().await;
    seed_cargo(&pool, &shipper_id, "BKG-3001", "CONFIRMED").await;
    let cookie = login_as(&app, "designer").await;

    let resp = post_form(
        &app,
        "/bookings/BKG-3001/issue-tracking-number",
        &cookie,
        "",
    )
    .await;
    assert_eq!(resp.status(), StatusCode::SEE_OTHER);

    assert_eq!(cargo_status(&pool, "BKG-3001").await, "TRACKING_ISSUED");
    let (number, status) = tracking_number_for(&pool, "BKG-3001")
        .await
        .expect("追跡活動が生成される");
    assert!(number.starts_with("TRK-"));
    assert_eq!(status, "NOT_RECEIVED");
    // US14 受入: 荷主へ追跡番号を通知（記録）する。
    assert_eq!(
        notification_count(&pool, "BKG-3001", "TRACKING_NUMBER_ISSUED").await,
        1
    );
}

#[tokio::test]
async fn us14_未確定予約への追跡発行は422() {
    let (app, shipper_id, pool, _c) = setup().await;
    seed_cargo(&pool, &shipper_id, "BKG-3002", "PRELIMINARY").await;
    let cookie = login_as(&app, "designer").await;

    let resp = post_form(
        &app,
        "/bookings/BKG-3002/issue-tracking-number",
        &cookie,
        "",
    )
    .await;
    assert_eq!(resp.status(), StatusCode::UNPROCESSABLE_ENTITY);
    assert!(tracking_number_for(&pool, "BKG-3002").await.is_none());
}

#[tokio::test]
async fn us15_荷役記録で追跡状態が自動更新される() {
    let (app, shipper_id, pool, _c) = setup().await;
    seed_cargo(&pool, &shipper_id, "BKG-3003", "CONFIRMED").await;
    let designer = login_as(&app, "designer").await;
    post_form(
        &app,
        "/bookings/BKG-3003/issue-tracking-number",
        &designer,
        "",
    )
    .await;
    let (number, _) = tracking_number_for(&pool, "BKG-3003").await.unwrap();

    let handler = login_as(&app, "handler").await;
    let body = format!(
        "tracking_number={number}&handling_type=RECEIVE&un_locode=JPOSA\
         &completion_time=2026-05-01T10:00&voyage_number=&operator_name=作業員A&receipt_confirmation="
    );
    let resp = post_form(&app, "/handling", &handler, &body).await;
    // ルート未確定のため警告なしでリダイレクト、または警告表示（200）。いずれも記録は成立。
    assert!(resp.status() == StatusCode::SEE_OTHER || resp.status() == StatusCode::OK);

    let (_, status) = tracking_number_for(&pool, "BKG-3003").await.unwrap();
    assert_eq!(status, "RECEIVED");
    // US15 受入: 記録後、荷主へ状態変更通知が送信（記録）される。
    assert_eq!(
        notification_count(&pool, "BKG-3003", "CARGO_STATUS_CHANGED").await,
        1
    );
}

#[tokio::test]
async fn us16_引取は荷受人確認がないと記録されない() {
    let (app, shipper_id, pool, _c) = setup().await;
    seed_cargo(&pool, &shipper_id, "BKG-3004", "CONFIRMED").await;
    let designer = login_as(&app, "designer").await;
    post_form(
        &app,
        "/bookings/BKG-3004/issue-tracking-number",
        &designer,
        "",
    )
    .await;
    let (number, _) = tracking_number_for(&pool, "BKG-3004").await.unwrap();

    let handler = login_as(&app, "handler").await;
    // 荷受人確認なしの引取 → エラー表示（200・記録されない）。
    let body = format!(
        "tracking_number={number}&handling_type=CLAIM&un_locode=USLAX\
         &completion_time=2026-05-30T10:00&voyage_number=&operator_name=&receipt_confirmation="
    );
    let resp = post_form(&app, "/handling", &handler, &body).await;
    assert_eq!(resp.status(), StatusCode::OK);
    let (_, status) = tracking_number_for(&pool, "BKG-3004").await.unwrap();
    assert_eq!(status, "NOT_RECEIVED"); // 引取は成立していない

    // 荷受人確認ありの引取 → 引取済へ。
    let body_ok = format!(
        "tracking_number={number}&handling_type=CLAIM&un_locode=USLAX\
         &completion_time=2026-05-30T10:00&voyage_number=&operator_name=&receipt_confirmation=署名"
    );
    let resp_ok = post_form(&app, "/handling", &handler, &body_ok).await;
    assert!(resp_ok.status() == StatusCode::SEE_OTHER || resp_ok.status() == StatusCode::OK);
    let (_, status_ok) = tracking_number_for(&pool, "BKG-3004").await.unwrap();
    assert_eq!(status_ok, "CLAIMED");
}

#[tokio::test]
async fn us17_追跡管理者が貨物状態を手動更新できる() {
    let (app, shipper_id, pool, _c) = setup().await;
    seed_cargo(&pool, &shipper_id, "BKG-3005", "CONFIRMED").await;
    let designer = login_as(&app, "designer").await;
    post_form(
        &app,
        "/bookings/BKG-3005/issue-tracking-number",
        &designer,
        "",
    )
    .await;
    let (number, _) = tracking_number_for(&pool, "BKG-3005").await.unwrap();

    let tracker = login_as(&app, "tracker").await;
    let body =
        "status=ONBOARD_CARRIER&un_locode=JPOSA&event_time=2026-05-02T09:00&voyage_number=V0001";
    let resp = post_form(&app, &format!("/tracking/{number}/updates"), &tracker, body).await;
    assert_eq!(resp.status(), StatusCode::SEE_OTHER);

    let (_, status) = tracking_number_for(&pool, "BKG-3005").await.unwrap();
    assert_eq!(status, "ONBOARD_CARRIER");
    // US17 受入: 状態変更の種類に応じて荷主への通知が送信（記録）される。
    assert_eq!(
        notification_count(&pool, "BKG-3005", "CARGO_STATUS_CHANGED").await,
        1
    );
}
